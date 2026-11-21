#!/usr/bin/env python3
"""Encrypt/decrypt an already verified archive locally. Never connects to a database.

Uses installed OpenSSL 3 CMS AES-256-GCM and RSA-OAEP/SHA-256. Recipient
identity/key custody require operator acceptance. No keys are generated here.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tarfile
import tempfile

spec = importlib.util.spec_from_file_location('database_backup', Path(__file__).with_name('database-backup.py'))
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)


def run(arguments):
    try:
        result = subprocess.run(['openssl', *arguments], capture_output=True, timeout=1800)
    except (OSError, subprocess.TimeoutExpired):
        raise backup.BackupError('OpenSSL unavailable or timed out; no acceptance recorded.') from None
    if result.returncode:
        raise backup.BackupError('OpenSSL verification/encryption failed; private diagnostics withheld.')
    return result.stdout


def ordinary(path, private=False):
    if path.is_symlink() or not path.is_file():
        raise backup.BackupError('Expected an ordinary file, without symbolic links.')
    if private and stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise backup.BackupError('Private keys/passphrase files require mode 0600 or stricter.')


def recipient(cert):
    ordinary(cert)
    run(['x509', '-in', str(cert), '-checkend', '0', '-noout'])
    der = run(['x509', '-in', str(cert), '-outform', 'DER'])
    public = run(['x509', '-in', str(cert), '-pubkey', '-noout'])
    # Require a reviewed RSA key of at least 3072 bits; reject unsupported key types.
    with tempfile.TemporaryDirectory(prefix='custerra-public-recipient-') as folder:
        path = Path(folder) / 'public.pem'
        path.write_bytes(public)
        details = run(['rsa', '-pubin', '-in', str(path), '-text', '-noout']).decode()
    import re
    bits = re.search(r'Public-Key: \((\d+) bit\)', details)
    if not bits or int(bits.group(1)) < 3072:
        raise backup.BackupError('Recipient requires RSA 3072 bits or stronger.')
    return hashlib.sha256(der).hexdigest()


def new_directory(path):
    path.mkdir(mode=0o700, parents=False, exist_ok=False)
    path.chmod(0o700)


def encrypt(archive, cert, destination):
    fingerprint = recipient(cert)
    ordinary(archive / 'database.dump')
    ordinary(archive / 'manifest.json')
    if archive.is_symlink():
        raise backup.BackupError('Archive directory must not be a symbolic link.')
    backup.verify(archive)
    new_directory(destination)
    # Plaintext tar exists only inside a private transient directory.
    with tempfile.TemporaryDirectory(prefix='.seal-', dir=destination) as folder:
        package = Path(folder) / 'archive.tar'
        with tarfile.open(package, 'w') as output:
            for name in ('database.dump', 'manifest.json'):
                output.add(archive / name, arcname=name, recursive=False)
        package.chmod(0o600)
        ciphertext = destination / 'database.cms'
        run(['cms', '-encrypt', '-binary', '-aes-256-gcm', '-outform', 'DER',
             '-in', str(package), '-out', str(ciphertext), '-recip', str(cert),
             '-keyopt', 'rsa_padding_mode:oaep', '-keyopt', 'rsa_oaep_md:sha256',
             '-keyopt', 'rsa_mgf1_md:sha256'])
        ciphertext.chmod(0o600)
    manifest = {'formatVersion': 1, 'createdAt': datetime.now(timezone.utc).isoformat(),
                'cipher': 'CMS_AES_256_GCM_RSA_OAEP_SHA256', 'archive': 'database.cms',
                'bytes': ciphertext.stat().st_size, 'sha256': backup.digest(ciphertext),
                'recipientCertificateSha256': fingerprint, 'decryptVerified': False,
                'limitations': 'Requires trusted artifact provenance and separate recovery-key custody. No live backup acceptance implied.'}
    with (destination / 'manifest.json').open('x') as output:
        json.dump(manifest, output, indent=2)
        output.write('\n')
    (destination / 'manifest.json').chmod(0o600)
    return manifest


def decrypt(bundle, cert, key, destination, passphrase_file=None):
    ordinary(bundle / 'manifest.json')
    ordinary(bundle / 'database.cms')
    ordinary(key, private=True)
    if bundle.is_symlink():
        raise backup.BackupError('Bundle directory must not be a symbolic link.')
    manifest = json.loads((bundle / 'manifest.json').read_text())
    if (manifest.get('formatVersion') != 1 or manifest.get('archive') != 'database.cms'
            or manifest.get('cipher') != 'CMS_AES_256_GCM_RSA_OAEP_SHA256'
            or manifest.get('recipientCertificateSha256') != recipient(cert)
            or manifest.get('bytes') != (bundle / 'database.cms').stat().st_size
            or manifest.get('sha256') != backup.digest(bundle / 'database.cms')):
        raise backup.BackupError('Encrypted bundle integrity/recipient verification failed.')
    if destination.exists() or destination.is_symlink():
        raise backup.BackupError('Decryption destination must be NEW; nothing will be overwritten.')
    if passphrase_file:
        ordinary(passphrase_file, private=True)
    with tempfile.TemporaryDirectory(prefix='.unseal-', dir=destination.parent) as folder:
        package = Path(folder) / 'archive.tar'
        arguments = ['cms', '-decrypt', '-binary', '-inform', 'DER', '-in', str(bundle / 'database.cms'),
                     '-recip', str(cert), '-inkey', str(key), '-out', str(package)]
        # No passphrase in argv or output; encrypted keys need an operator-managed private file.
        arguments += ['-passin', 'file:' + str(passphrase_file)] if passphrase_file else ['-passin', 'pass:']
        run(arguments)
        # OpenSSL must accept the GCM tag before anything can be extracted/accepted.
        staged = Path(folder) / 'verified'
        new_directory(staged)
        with tarfile.open(package, 'r:') as source:
            members = source.getmembers()
            if len(members) != 2 or {m.name for m in members} != {'database.dump', 'manifest.json'} or any(not m.isfile() for m in members):
                raise backup.BackupError('Unexpected archive members; extraction refused.')
            for member in members:
                reader = source.extractfile(member)
                with (staged / member.name).open('xb') as output:
                    import shutil
                    shutil.copyfileobj(reader, output)
                (staged / member.name).chmod(0o600)
        backup.verify(staged)
        # Atomic rename on the destination filesystem only after all verification passes.
        staged.rename(destination)
    return {'status': 'DECRYPTED_AND_ARCHIVE_VERIFIED', 'cipherSha256': manifest['sha256'],
            'databaseRestoreAcceptance': 'PENDING', 'sourceBundleManifestUnchanged': True}


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    seal = commands.add_parser('encrypt')
    seal.add_argument('--archive-directory', type=Path, required=True)
    unseal = commands.add_parser('decrypt')
    unseal.add_argument('--bundle-directory', type=Path, required=True)
    unseal.add_argument('--private-key', type=Path, required=True)
    unseal.add_argument('--key-passphrase-file', type=Path)
    for command in (seal, unseal):
        command.add_argument('--recipient-certificate', type=Path, required=True)
        command.add_argument('--destination', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'encrypt':
            encrypt(args.archive_directory, args.recipient_certificate, args.destination)
            print('Encrypted bundle created; decryption, independent storage and restore acceptance still required.')
        else:
            decrypt(args.bundle_directory, args.recipient_certificate, args.private_key, args.destination, args.key_passphrase_file)
            print('Authenticated decryption and inner archive verification passed; database restore remains required.')
    except (backup.BackupError, OSError, ValueError, KeyError, tarfile.TarError):
        print('Encrypted backup operation failed; no acceptance recorded. Existing data was not overwritten.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
