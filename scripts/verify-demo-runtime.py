#!/usr/bin/env python3
"""Disposable combined-demo runtime rehearsal for hosted CI, using synthetic data.

No production credentials or resources. Only this run's containers/networks/volume
are removed. All provider URLs point to an unreachable local port; no polling/push.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request

spec = importlib.util.spec_from_file_location('container_fixture', Path(__file__).with_name('verify-container-replacement.py'))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    prefix = 'custerra-phase27-ci-' + secrets.token_hex(5)
    internal, edge, volume, database, demo = [prefix + suffix for suffix in ('-internal', '-edge', '-data', '-db', '-app')]
    password = secrets.token_urlsafe(24)
    port = fixture.port()
    base = f'http://127.0.0.1:{port}'
    containers, networks = [], []
    created_volume = False
    args.report.parent.mkdir(parents=True, exist_ok=True)

    def sql(query):
        return fixture.docker('exec', database, 'psql', '-U', 'custerra', '-d', 'custerra_ci_test', '-tA', '-c', query)

    def start_demo():
        fixture.docker('run', '-d', '--name', demo, '--network', edge, '--memory', '512m', '--cpus', '0.5',
            '-p', f'127.0.0.1:{port}:10000', '-e', 'DB_URL=jdbc:postgresql://db:5432/custerra_ci_test',
            '-e', 'DB_USERNAME=custerra', '-e', 'DB_PASSWORD=' + password, '-e', 'OPS_TOKEN=' + secrets.token_urlsafe(32),
            '-e', 'CORS_ALLOWED_ORIGINS=' + base, '-e', 'MONITORING_ENABLED=false', '-e', 'NOTIFICATIONS_ENABLED=false',
            '-e', 'GDACS_ENABLED=false', '-e', 'FIRMS_MAP_KEY=',
            '-e', 'USGS_QUERY_URL=https://127.0.0.1:9/query', '-e', 'OPEN_METEO_URL=https://127.0.0.1:9/weather',
            '-e', 'GEOCODING_URL=https://127.0.0.1:9/search', '-e', 'ROUTING_URL=https://127.0.0.1:9/route', args.image)
        fixture.docker('network', 'connect', internal, demo)

    def inventory():
        tables = sql("select tablename from pg_tables where schemaname='public' order by tablename").splitlines()
        return {table: sql('select md5(coalesce(string_agg(row_to_json(t)::text,chr(10) order by row_to_json(t)::text),\'\')) from public."' + table.replace('"', '""') + '" t') for table in tables}

    try:
        fixture.docker('network', 'create', '--internal', internal); networks.append(internal)
        fixture.docker('network', 'create', edge); networks.append(edge)
        fixture.docker('volume', 'create', volume); created_volume = True
        fixture.docker('run', '-d', '--name', database, '--network', internal, '--network-alias', 'db',
            '-e', 'POSTGRES_USER=custerra', '-e', 'POSTGRES_DB=custerra_ci_test', '-e', 'POSTGRES_PASSWORD=' + password,
            '-v', volume + ':/var/lib/postgresql/data', 'postgres:17'); containers.append(database)
        for _ in range(60):
            try:
                sql('select 1'); break
            except RuntimeError:
                time.sleep(1)
        else:
            raise RuntimeError('Synthetic database startup timed out.')
        containers.append(demo); start_demo()
        fixture.ready(base + '/health')
        smoke = subprocess.run([sys.executable, str(Path(__file__).with_name('verify-local-deployment.py'))],
            env=os.environ | {'CUSTERRA_SMOKE_BASE_URL': base, 'CUSTERRA_SMOKE_READINESS_URL': base + '/health'},
            capture_output=True, text=True, timeout=150)
        if smoke.returncode:
            raise RuntimeError('Combined-demo HTTP/security smoke failed.')
        before = inventory()
        outage_start = time.monotonic()
        fixture.docker('stop', '--time', '35', database)
        try:
            urllib.request.urlopen(base + '/health', timeout=6)
            raise AssertionError('Readiness concealed database outage.')
        except urllib.error.HTTPError as error:
            assert error.code == 503 and json.load(error) == {'status': 'DOWN'}
        fixture.docker('start', database)
        fixture.ready(base + '/health')
        database_recovery = time.monotonic() - outage_start
        replacement_start = time.monotonic()
        fixture.docker('stop', '--time', '35', demo)
        fixture.docker('rm', demo)
        start_demo()
        fixture.ready(base + '/health')
        replacement_seconds = time.monotonic() - replacement_start
        assert inventory() == before
        assert sql('select count(*) from flyway_schema_history where success') == '11'
        report = {'scope': 'HOSTED_CI_DISPOSABLE_COMBINED_DEMO_SYNTHETIC_ONLY', 'memoryLimitMiB': 512,
            'cpuLimit': 0.5, 'migrations': 11, 'httpSessionCsrfProxyAndB2b': 'PASS',
            'databaseOutageReadiness503': True, 'databaseStopToRecoverySeconds': round(database_recovery, 3),
            'combinedReplacementRecoverySeconds': round(replacement_seconds, 3), 'allTableDigestsUnchanged': True,
            'observedRowLoss': 0, 'outOfMemoryKilled': fixture.docker('inspect', '--format', '{{.State.OOMKilled}}', demo),
            'memoryUsageOneSnapshot': fixture.docker('stats', '--no-stream', '--format', '{{.MemUsage}}', demo),
            'imageId': fixture.docker('image', 'inspect', '--format', '{{.Id}}', args.image),
            'publicProviderRequests': 0, 'realPushSent': False, 'productionAcceptance': False}
        args.report.write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report))
    except Exception:
        try:
            (args.report.parent / 'synthetic-demo-runtime.log').write_text(fixture.docker('logs', demo))
        except RuntimeError:
            pass
        raise
    finally:
        for name in reversed(containers):
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True, timeout=45)
        if created_volume:
            subprocess.run(['docker', 'volume', 'rm', volume], capture_output=True, timeout=30)
        for name in reversed(networks):
            subprocess.run(['docker', 'network', 'rm', name], capture_output=True, timeout=30)


if __name__ == '__main__':
    main()
