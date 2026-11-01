#!/usr/bin/env python3
"""Non-destructive recovery drill using an explicitly selected LOCAL SYNTHETIC test database."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import tempfile
import time
import uuid

spec = importlib.util.spec_from_file_location('backup', Path(__file__).with_name('database-backup.py'))
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)


def inventory(env):
    tables = backup.sql("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;", env).splitlines()
    # Compare every row's representation, including owner IDs and migration checksums, without exposing values.
    return {table: backup.sql('SELECT md5(coalesce(string_agg(row_to_json(t)::text, chr(10) ORDER BY row_to_json(t)::text),\'\')) FROM public."'
                              + table.replace('"', '""') + '" t;', env) for table in tables}


def rejected(call):
    try:
        call()
    except backup.BackupError:
        return
    raise AssertionError('Unsafe or corrupt operation was not refused')


def main():
    os.umask(0o077)
    source = backup.connection_environment()
    if source['PGHOST'] != '127.0.0.1' or not source['PGDATABASE'].startswith('custerra_') or not source['PGDATABASE'].endswith('_test'):
        raise backup.BackupError('Select an isolated local custerra_*_test synthetic database.')
    if source.get('PGHOSTADDR') not in (None, '', '127.0.0.1'):
        raise backup.BackupError('Unexpected host override.')
    folder = Path(tempfile.mkdtemp(prefix='custerra-recovery-drill-'))
    archive = folder / 'backup'
    started = time.monotonic()
    before = inventory(source)
    backup.backup(archive)
    backup.verify(archive)
    target_name = 'custerra_restore_' + uuid.uuid4().hex[:12]
    backup.run(['createdb', '--no-password', target_name], env=source)
    os.environ['PGDATABASE'] = target_name
    target = backup.connection_environment()
    restore_started = time.monotonic()
    backup.restore(archive)
    restore_seconds = time.monotonic() - restore_started
    assert inventory(target) == before, 'Restored data differs from source'
    assert inventory(source) == before, 'Source database changed'
    rejected(lambda: backup.restore(archive))
    assert inventory(target) == before, 'Refused restore changed destination'
    corrupt = folder / 'corrupt'
    shutil.copytree(archive, corrupt)
    with (corrupt / 'database.dump').open('ab') as output:
        output.write(b'corrupt')
    rejected(lambda: backup.restore(corrupt))
    os.environ['PGHOST'] = 'example.invalid'
    os.environ['PGSSLMODE'] = 'verify-full'
    rejected(lambda: backup.restore(archive))
    os.environ['PGHOST'] = source['PGHOST']
    os.environ['PGDATABASE'] = 'custerra_missing_' + uuid.uuid4().hex[:12]
    rejected(lambda: backup.backup(folder / 'failed'))
    assert not (folder / 'failed' / 'manifest.json').exists()
    report = {'checksPassed': ['archive integrity', 'transactional restore', 'all table contents identical',
                             'source unchanged', 'populated destination refused unchanged', 'corruption refused',
                             'remote destination refused', 'failed backup has no acceptance manifest'],
              'archiveDirectory': str(archive), 'restoreDatabase': target_name,
              'productionDataUsed': False, 'restoreSeconds': round(restore_seconds, 3),
              'drillSeconds': round(time.monotonic() - started, 3), 'observedRowLoss': 0, 'applicationAcceptance': 'PENDING'}
    (folder / 'drill.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
