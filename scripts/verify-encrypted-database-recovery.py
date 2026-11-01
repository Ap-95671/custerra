#!/usr/bin/env python3
"""Local synthetic-only encryption/decryption/database restore drill.

Never accepts a remote source. Generates only disposable test recovery keys, and
removes all temporary archives/keys at exit. A new synthetic restore DB is retained.
"""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    secure = module('secure_backup', 'encrypted-database-backup.py')
    drill = module('recovery_drill', 'verify-database-recovery.py')
    source = secure.backup.connection_environment()
    if source['PGHOST'] != '127.0.0.1' or not source['PGDATABASE'].startswith('custerra_') or not source['PGDATABASE'].endswith('_test'):
        raise secure.backup.BackupError('Use an isolated loopback custerra_*_test synthetic database.')
    if source.get('PGHOSTADDR') not in (None, '', '127.0.0.1'):
        raise secure.backup.BackupError('Unexpected host override.')
    before = drill.inventory(source)
    began = time.monotonic()
    with tempfile.TemporaryDirectory(prefix='custerra-encrypted-fixture-') as folder:
        root = Path(folder)
        key, cert = root / 'fixture-key.pem', root / 'fixture-cert.pem'
        result = subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-days', '1',
                                 '-subj', '/CN=Custerra disposable synthetic recovery/', '-keyout', str(key),
                                 '-out', str(cert)], capture_output=True, timeout=30)
        if result.returncode:
            raise secure.backup.BackupError('Synthetic key generation failed; private diagnostics withheld.')
        key.chmod(0o600)
        archive, encrypted, decrypted = [root / name for name in ('archive', 'encrypted', 'decrypted')]
        secure.backup.backup(archive)
        plain_manifest = secure.backup.verify(archive)
        cipher_manifest = secure.encrypt(archive, cert, encrypted)
        secure.decrypt(encrypted, cert, key, decrypted)
        assert secure.backup.digest(archive / 'database.dump') == secure.backup.digest(decrypted / 'database.dump')
        assert (archive / 'manifest.json').read_bytes() == (decrypted / 'manifest.json').read_bytes()
        target_name = 'custerra_restore_' + uuid.uuid4().hex[:12]
        secure.backup.run(['createdb', '--no-password', target_name], env=source)
        os.environ['PGDATABASE'] = target_name
        target = secure.backup.connection_environment()
        restore_started = time.monotonic()
        secure.backup.restore(decrypted)
        restore_seconds = time.monotonic() - restore_started
        assert drill.inventory(target) == before
        assert drill.inventory(source) == before
        report = {'executedAt': datetime.now(timezone.utc).isoformat(),
                  'scope': 'LOCAL_SYNTHETIC_ONLY_NO_LIVE_EXPORT', 'sourceDatabaseCategory': 'isolated_local_fixture',
                  'encryption': cipher_manifest['cipher'], 'plainArchiveBytes': plain_manifest['bytes'],
                  'encryptedBundleBytes': cipher_manifest['bytes'], 'archiveSha256': plain_manifest['sha256'],
                  'cipherSha256': cipher_manifest['sha256'], 'authenticatedDecryption': 'PASS',
                  'innerManifestUnchanged': True, 'allTableDigestsEqual': True, 'sourceUnchanged': True,
                  'observedRowLoss': 0, 'restoreSeconds': round(restore_seconds, 3),
                  'totalDrillSeconds': round(time.monotonic() - began, 3), 'restoreDatabase': target_name,
                  'recoveryKeys': 'DISPOSABLE_TEST_KEYS_DESTROYED_AFTER_DRILL',
                  'independentLiveStorageAcceptance': 'PENDING', 'liveBackupAccepted': False}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
