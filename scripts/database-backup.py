#!/usr/bin/env python3
"""Explicit pg_dump backup and local, empty-database restore. No secrets in arguments/logs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone


class BackupError(Exception):
    pass


def run(command, *, env=None, input=None):
    try:
        result = subprocess.run(command, env=env, input=input, capture_output=True, text=True, timeout=1800)
    except (OSError, subprocess.TimeoutExpired):
        raise BackupError(f'{command[0]} unavailable or timed out; connection details withheld.') from None
    if result.returncode:
        raise BackupError(f'{command[0]} failed; connection details and database output withheld.')
    # A dump warning must be reviewed, never silently accepted as a clean backup.
    if command[0] == 'pg_dump' and result.stderr.strip():
        raise BackupError('pg_dump emitted warnings; backup is not accepted. Review privately with your database operator.')
    return result.stdout


def connection_environment():
    env = os.environ.copy()
    if not env.get('PGDATABASE') or not re.fullmatch(r'[A-Za-z0-9_]+', env['PGDATABASE']):
        raise BackupError('Set PGDATABASE to a plain database name, not a connection URI.')
    if not env.get('PGHOST') or not env.get('PGUSER'):
        raise BackupError('Set PGHOST and PGUSER explicitly; use a mode-0600 PGPASSFILE for credentials.')
    # Avoid hidden service-file overrides changing the connection target.
    env.pop('PGSERVICE', None)
    env.pop('PGSERVICEFILE', None)
    env['PGCONNECT_TIMEOUT'] = '10'
    if env['PGHOST'] not in ('localhost', '127.0.0.1', '::1') and not env['PGHOST'].startswith('/'):
        if env.get('PGSSLMODE') != 'verify-full':
            raise BackupError('Remote backups require PGSSLMODE=verify-full and a trusted root certificate.')
    return env


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def backup(directory):
    env = connection_environment()
    directory.mkdir(mode=0o700, parents=False, exist_ok=False)
    archive = directory / 'database.dump'
    run(['pg_dump', '--no-password', '--format=custom', '--no-owner', '--no-acl', '--file', str(archive)], env=env)
    archive.chmod(0o600)
    run(['pg_restore', '--list', str(archive)], env=env)
    manifest = {'formatVersion': 1, 'createdAt': datetime.now(timezone.utc).isoformat(),
                'archive': 'database.dump', 'bytes': archive.stat().st_size, 'sha256': digest(archive),
                'restoreVerified': False, 'pgDumpVersion': run(['pg_dump', '--version']).strip()}
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Archive and integrity manifest created. Restore acceptance is still required; this is not an encrypted backup.')


def verify(directory):
    manifest = json.loads((directory / 'manifest.json').read_text())
    archive = directory / 'database.dump'
    if manifest.get('formatVersion') != 1 or manifest.get('archive') != archive.name:
        raise BackupError('Unsupported manifest.')
    if archive.stat().st_size != manifest['bytes'] or digest(archive) != manifest['sha256']:
        raise BackupError('Backup integrity verification failed; restore refused.')
    run(['pg_restore', '--list', str(archive)])
    return manifest


def sql(query, env):
    return run(['psql', '-X', '--no-password', '-A', '-t', '-v', 'ON_ERROR_STOP=1'], env=env, input=query).strip()


def restore(directory):
    manifest = verify(directory)
    env = connection_environment()
    if env['PGHOST'] not in ('localhost', '127.0.0.1', '::1') or env.get('PGHOSTADDR') not in (None, '', '127.0.0.1', '::1'):
        raise BackupError('Restore is restricted to a local isolated database.')
    if not env['PGDATABASE'].startswith('custerra_restore_'):
        raise BackupError('Restore database name must start with custerra_restore_. Create a NEW empty local database.')
    # Verify actual server address as well as the configured name.
    if sql("SELECT inet_server_addr() IN ('127.0.0.1'::inet, '::1'::inet);", env) != 't':
        raise BackupError('Destination is not a loopback PostgreSQL server.')
    objects = sql("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%';", env)
    functions = sql("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema');", env)
    schemas = sql("SELECT count(*) FROM pg_namespace WHERE nspname NOT IN ('public','pg_catalog','information_schema') AND nspname NOT LIKE 'pg_toast%' AND nspname NOT LIKE 'pg_temp%';", env)
    if objects != '0' or functions != '0' or schemas != '0':
        raise BackupError('Destination is not empty. No existing database will be overwritten.')
    started = datetime.now(timezone.utc)
    run(['pg_restore', '--no-password', '--exit-on-error', '--single-transaction', '--no-owner', '--no-acl',
         '--dbname', env['PGDATABASE'], str(directory / 'database.dump')], env=env)
    migrations = json.loads(sql("SELECT coalesce(json_agg(row_to_json(x)), '[]') FROM (SELECT version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank) x;", env))
    if not migrations or any(not row['success'] for row in migrations):
        raise BackupError('Restore completed but Flyway history is missing or failed; acceptance refused.')
    tables = sql("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;", env).splitlines()
    counts = {table: int(sql('SELECT count(*) FROM public."' + table.replace('"', '""') + '";', env)) for table in tables}
    report = {'archiveSha256': manifest['sha256'], 'restoredAt': started.isoformat(),
              'durationSeconds': (datetime.now(timezone.utc) - started).total_seconds(),
              'migrations': migrations, 'rowCounts': counts,
              'status': 'RESTORED_PENDING_APPLICATION_ACCEPTANCE',
              'limitations': 'Validate Flyway checksums and application ownership/readiness against this restored database. No production backup acceptance implied.'}
    # Separate evidence: never overwrite a previous drill or change the original manifest.
    with (directory / ('restore-' + started.strftime('%Y%m%dT%H%M%S%fZ') + '.json')).open('x') as output:
        json.dump(report, output, indent=2)
        output.write('\n')
    print('Restored to the empty isolated database. Migration history and table counts recorded; application acceptance remains required.')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['backup', 'verify', 'restore'])
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    try:
        if args.action == 'backup': backup(args.directory)
        elif args.action == 'restore': restore(args.directory)
        else:
            verify(args.directory)
            print('Checksum, size and archive directory verified. This does not verify restoration.')
    except BackupError as failure:
        print(str(failure), file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError):
        # No raw tool stderr, credentials, payloads or database values in logs.
        print('Backup operation failed; no acceptance recorded. Check configuration, archive integrity and destination requirements.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
