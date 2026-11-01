#!/usr/bin/env python3
"""Bounded, disposable Docker rehearsal. Uses only synthetic data and loopback ports.

Requires locally built custerra-api:local-v2 and custerra-web:local-v2 images.
The internal Docker network blocks external provider access. All resources created
by this invocation are removed in finally; pre-existing resources are untouched.
"""
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import urllib.request
import urllib.error


def docker(*args):
    result = subprocess.run(['docker', *args], capture_output=True, text=True, timeout=180)
    if result.returncode:
        # Docker errors may repeat environment arguments. Never print raw output.
        raise RuntimeError('Local Docker rehearsal operation failed; inspect Docker privately.')
    return result.stdout.strip()


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def ready(url):
    for _ in range(90):
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if json.load(response)['status'] == 'UP':
                    return
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(1)
    raise RuntimeError('Local rehearsal readiness deadline exceeded.')


def main():
    prefix = 'custerra-v2-rehearsal-' + secrets.token_hex(6)
    network, edge, volume = prefix + '-net', prefix + '-edge', prefix + '-data'
    db, api, web = [prefix + suffix for suffix in ('-db', '-api', '-web')]
    password, operations = secrets.token_urlsafe(24), secrets.token_urlsafe(32)
    web_port = port()
    web_url = f'http://127.0.0.1:{web_port}'
    containers = []
    created_network = created_edge = created_volume = False

    def start_api():
        docker('run', '-d', '--name', api, '--network', network, '--network-alias', 'backend',
               '--memory', '768m',
               '-e', 'DB_URL=jdbc:postgresql://db:5432/custerra_container_test',
               '-e', 'DB_USERNAME=custerra', '-e', 'DB_PASSWORD=' + password,
               '-e', 'OPS_TOKEN=' + operations, '-e', 'MONITORING_ENABLED=false',
               '-e', 'NOTIFICATIONS_ENABLED=false', '-e', 'GDACS_ENABLED=false',
               '-e', 'CORS_ALLOWED_ORIGINS=' + web_url, 'custerra-api:local-v2')

    def sql(query):
        return docker('exec', db, 'psql', '-U', 'custerra', '-d', 'custerra_container_test', '-tA', '-c', query)

    try:
        docker('network', 'create', '--internal', network); created_network = True
        docker('network', 'create', edge); created_edge = True
        docker('volume', 'create', volume); created_volume = True
        docker('run', '-d', '--name', db, '--network', network, '--network-alias', 'db',
               '-e', 'POSTGRES_USER=custerra', '-e', 'POSTGRES_DB=custerra_container_test',
               '-e', 'POSTGRES_PASSWORD=' + password, '-v', volume + ':/var/lib/postgresql/data', 'postgres:17')
        containers.append(db)
        for _ in range(60):
            try:
                sql('select 1'); break
            except RuntimeError:
                time.sleep(1)
        else:
            raise RuntimeError('Isolated container database failed to become ready.')
        start_api(); containers.append(api)
        docker('run', '-d', '--name', web, '--network', edge,
               '-p', f'127.0.0.1:{web_port}:3000', '--memory', '384m',
               '-e', 'BACKEND_INTERNAL_URL=http://backend:8080', 'custerra-web:local-v2')
        containers.append(web)
        docker('network', 'connect', network, web)
        ready(web_url + '/health')
        env = os.environ | {'CUSTERRA_SMOKE_BASE_URL': web_url,
                            'CUSTERRA_SMOKE_READINESS_URL': web_url + '/health'}
        smoke = subprocess.run([sys.executable, str(Path(__file__).with_name('verify-local-deployment.py'))],
                               env=env, capture_output=True, text=True, timeout=150)
        if smoke.returncode:
            raise RuntimeError('Container HTTP/session/CSRF/key smoke failed; raw credentials are suppressed.')
        inventory_sql = "select md5(coalesce(string_agg(row_to_json(t)::text,chr(10) order by row_to_json(t)::text),'')) from user_account t"
        before = sql(inventory_sql)
        began = time.monotonic()
        docker('stop', '--time', '35', db)
        try:
            urllib.request.urlopen(web_url + '/health', timeout=5)
            raise AssertionError('Web readiness concealed the database outage.')
        except urllib.error.HTTPError as error:
            assert error.code == 503
            assert json.load(error) == {'status': 'DOWN'}
        docker('start', db)
        ready(web_url + '/health')
        database_recovery = time.monotonic() - began
        began = time.monotonic()
        docker('stop', '--time', '35', api)
        docker('rm', api)
        start_api()
        ready(web_url + '/health')
        elapsed = time.monotonic() - began
        assert sql(inventory_sql) == before, 'Synthetic account rows changed during replacement.'
        # Exercise the existing web process after backend address replacement.
        with urllib.request.urlopen(web_url + '/api/v1/health', timeout=10) as response:
            assert response.status == 200
        assert sql('select count(*) from flyway_schema_history where success') == '11'
        report = {'scope': 'LOCAL_DISPOSABLE_DOCKER_SYNTHETIC_DATA_NO_UPSTREAM_ACCESS',
                  'migrations': 11, 'httpSessionCsrfAndB2bSmoke': 'PASS',
                  'apiReplacementRecoverySeconds': round(elapsed, 3),
                  'databaseOutageReadiness503': True,
                  'databaseRestartRecoverySeconds': round(database_recovery, 3),
                  'syntheticAccountRowDigestUnchanged': True, 'observedAccountRowLoss': 0,
                  'webProxyAfterReplacement': 'PASS', 'externalAcceptance': False}
        destination = Path(__file__).resolve().parents[1] / 'docs/verification/container-rehearsal.json'
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report))
    finally:
        for name in reversed(containers):
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True, timeout=45)
        if created_volume:
            subprocess.run(['docker', 'volume', 'rm', volume], capture_output=True, timeout=30)
        if created_edge:
            subprocess.run(['docker', 'network', 'rm', edge], capture_output=True, timeout=30)
        if created_network:
            subprocess.run(['docker', 'network', 'rm', network], capture_output=True, timeout=30)


if __name__ == '__main__':
    main()
