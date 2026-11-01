import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('secure_backup', Path(__file__).resolve().parents[1] / 'encrypted-database-backup.py')
secure = importlib.util.module_from_spec(spec)
spec.loader.exec_module(secure)


class EncryptedBackupTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = tempfile.TemporaryDirectory(prefix='custerra-crypto-tests-')
        cls.base = Path(cls.root.name)
        cls.key, cls.cert = cls.base / 'fixture-key.pem', cls.base / 'fixture-cert.pem'
        result = subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-days', '1',
                                 '-subj', '/CN=Custerra disposable synthetic test/', '-keyout', str(cls.key),
                                 '-out', str(cls.cert)], capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError('Disposable OpenSSL test key generation failed; diagnostics suppressed.')
        cls.key.chmod(0o600)

    @classmethod
    def tearDownClass(cls):
        cls.root.cleanup()

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(dir=self.base)
        self.path = Path(self.folder.name)
        self.archive = self.path / 'archive'
        self.archive.mkdir(mode=0o700)
        self.payload = b'SYNTHETIC CRYPTO TRANSPORT TEST ONLY; NOT A LIVE DATABASE\n' * 100
        (self.archive / 'database.dump').write_bytes(self.payload)
        (self.archive / 'manifest.json').write_text(json.dumps({'formatVersion': 1, 'archive': 'database.dump',
             'bytes': len(self.payload), 'sha256': hashlib.sha256(self.payload).hexdigest()}))
        # Only the PostgreSQL parser invocation is mocked. OpenSSL/AEAD/filesystem checks are real.
        self.parser = patch.object(secure.backup, 'run', return_value='synthetic archive list')
        self.parser.start()

    def tearDown(self):
        self.parser.stop()
        self.folder.cleanup()

    def seal(self):
        destination = self.path / 'encrypted'
        secure.encrypt(self.archive, self.cert, destination)
        return destination

    def test_round_trip_is_private_and_preserves_immutable_manifest(self):
        bundle = self.seal()
        original = (bundle / 'manifest.json').read_bytes()
        destination = self.path / 'decrypted'
        report = secure.decrypt(bundle, self.cert, self.key, destination)
        self.assertEqual((destination / 'database.dump').read_bytes(), self.payload)
        self.assertEqual(report['status'], 'DECRYPTED_AND_ARCHIVE_VERIFIED')
        self.assertEqual((bundle / 'manifest.json').read_bytes(), original)
        self.assertFalse(json.loads(original)['decryptVerified'])
        self.assertEqual(bundle.stat().st_mode & 0o777, 0o700)
        self.assertTrue(all(p.stat().st_mode & 0o777 == 0o600 for p in bundle.iterdir()))
        self.assertNotIn(self.payload, (bundle / 'database.cms').read_bytes())

    def test_tampered_ciphertext_is_refused_before_plaintext_acceptance(self):
        bundle = self.seal()
        ciphertext = bundle / 'database.cms'
        data = bytearray(ciphertext.read_bytes())
        data[len(data) // 2] ^= 1
        ciphertext.write_bytes(data)
        # Recompute the untrusted checksum: authenticated encryption must still reject it.
        manifest = json.loads((bundle / 'manifest.json').read_text())
        manifest['sha256'] = secure.backup.digest(ciphertext)
        (bundle / 'manifest.json').write_text(json.dumps(manifest))
        destination = self.path / 'decrypted'
        with self.assertRaises(secure.backup.BackupError):
            secure.decrypt(bundle, self.cert, self.key, destination)
        self.assertFalse(destination.exists())
        self.assertFalse(list(self.path.glob('.unseal-*')))

    def test_corrupt_inner_archive_cannot_be_encrypted(self):
        (self.archive / 'database.dump').write_bytes(b'corrupted')
        with self.assertRaises(secure.backup.BackupError):
            self.seal()
        self.assertFalse((self.path / 'encrypted').exists())

    def test_existing_encryption_and_decryption_destinations_are_unchanged(self):
        bundle = self.seal()
        before = (bundle / 'database.cms').read_bytes()
        with self.assertRaises(FileExistsError):
            self.seal()
        destination = self.path / 'existing'
        destination.mkdir()
        marker = destination / 'keep'
        marker.write_text('preserve')
        with self.assertRaises(secure.backup.BackupError):
            secure.decrypt(bundle, self.cert, self.key, destination)
        self.assertEqual(marker.read_text(), 'preserve')
        self.assertEqual((bundle / 'database.cms').read_bytes(), before)

    def test_readable_or_linked_private_key_is_refused(self):
        bundle = self.seal()
        key = self.path / 'exposed-key.pem'
        shutil.copyfile(self.key, key)
        key.chmod(0o644)
        with self.assertRaises(secure.backup.BackupError):
            secure.decrypt(bundle, self.cert, key, self.path / 'destination')
        key.unlink()
        key.symlink_to(self.key)
        with self.assertRaises(secure.backup.BackupError):
            secure.decrypt(bundle, self.cert, key, self.path / 'destination')

    def test_changed_recipient_and_truncated_bundle_are_refused(self):
        bundle = self.seal()
        manifest = json.loads((bundle / 'manifest.json').read_text())
        manifest['recipientCertificateSha256'] = '0' * 64
        (bundle / 'manifest.json').write_text(json.dumps(manifest))
        with self.assertRaises(secure.backup.BackupError):
            secure.decrypt(bundle, self.cert, self.key, self.path / 'destination')
        bundle = self.path / 'truncated'
        shutil.copytree(self.path / 'encrypted', bundle)
        (bundle / 'database.cms').write_bytes(b'truncated')
        with self.assertRaises(secure.backup.BackupError):
            secure.decrypt(bundle, self.cert, self.key, self.path / 'destination')


if __name__ == '__main__':
    unittest.main()
