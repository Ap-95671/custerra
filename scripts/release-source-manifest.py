#!/usr/bin/env python3
"""Allowlisted, content-addressed source inventory; never initializes/replaces Git.

No copies or network requests. --upload-entries emits reviewed file contents for
the existing private repository connector; never send this output to public logs.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DIRECTORIES = ('backend/src', 'frontend/src', 'frontend/public', 'frontend/tests', 'frontend/e2e',
               'ml-service', 'research', 'scripts', 'docs', 'infrastructure', '.github')
FILES = ('README.md', '.gitignore', '.dockerignore', '.env.example', 'render.yaml', 'backend/pom.xml',
         'frontend/package.json', 'frontend/package-lock.json', 'frontend/next.config.ts',
         'frontend/next-env.d.ts', 'frontend/tsconfig.json', 'frontend/eslint.config.mjs',
         'frontend/vitest.config.ts', 'frontend/playwright.config.ts', 'frontend/.env.example')
EXCLUDED = {'node_modules', 'target', '.next', '.venv', '__pycache__', '.pytest_cache',
            '.git', 'test-results', 'playwright-report', 'backups', 'secrets'}
SENSITIVE_SUFFIXES = {'.pem', '.key', '.p12', '.pfx', '.dump', '.cms', '.age', '.pyc', '.log'}
PATTERNS = {
    'private-key-material': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'aws-access-key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'credentialed-postgres-uri': re.compile(r'postgres(?:ql)?://[^\s:@]+:[^\s@]{8,}@'),
}


def publishable(relative):
    return (not relative.is_relative_to('docs') and not relative.is_relative_to('research')
            and (relative.suffix.lower() not in {'.md', '.txt'} or relative.name == 'requirements.txt'))


def inventory():
    candidates = {ROOT / name for name in FILES}
    for directory in DIRECTORIES:
        candidates.update((ROOT / directory).rglob('*'))
    files, findings = [], []
    for path in sorted(candidates):
        relative = path.relative_to(ROOT)
        if any(part in EXCLUDED for part in relative.parts) or path.name == '.DS_Store':
            continue
        if path.name.startswith('.env') and path.name != '.env.example':
            continue
        if path.suffix in SENSITIVE_SUFFIXES or path.name.startswith('pgpass') or path.name == '.pgpass':
            continue
        if path.is_symlink():
            raise ValueError('Source symlinks require explicit review: ' + str(relative))
        if not path.is_file():
            continue
        data = path.read_bytes()
        try:
            content = data.decode('utf-8')
        except UnicodeDecodeError:
            content = None
        if content is not None:
            for rule, pattern in PATTERNS.items():
                for match in pattern.finditer(content):
                    findings.append({'path': str(relative), 'line': content[:match.start()].count('\n') + 1,
                                     'rule': rule})
        files.append({'path': str(relative), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
                      'gitBlobSha': hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest(),
                      'mode': '100755' if path.stat().st_mode & 0o111 else '100644', 'text': content is not None})
    return files, findings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--upload-entries', action='store_true')
    parser.add_argument('--index', action='store_true')
    parser.add_argument('--read-source')
    parser.add_argument('--offset', type=int, default=0)
    parser.add_argument('--length', type=int, default=12000)
    args = parser.parse_args()
    if args.read_source:
        relative = Path(args.read_source)
        path = ROOT / relative
        permitted = str(relative) in FILES or any(relative.is_relative_to(directory) for directory in DIRECTORIES)
        if (not permitted or not publishable(relative) or path.resolve() != path or not path.is_relative_to(ROOT)
                or any(part in EXCLUDED for part in relative.parts)
                or (path.name.startswith('.env') and path.name != '.env.example')
                or path.suffix in SENSITIVE_SUFFIXES or path.name == '.DS_Store'
                or path.name.startswith('pgpass') or path.name == '.pgpass'
                or args.offset < 0 or not 1 <= args.length <= 65536):
            raise ValueError('Requested file is outside reviewed source scope.')
        with path.open('rb') as source:
            source.seek(args.offset)
            print(base64.b64encode(source.read(args.length)).decode())
        return 0
    files, findings = inventory()
    if findings:
        print(json.dumps({'status': 'REQUIRES_REVIEW', 'findings': findings}))
        return 1
    core = [f for f in files if publishable(Path(f['path']))]
    canonical = '\n'.join(f['path'] + '\0' + f['sha256'] for f in core).encode()
    report = {'formatVersion': 1, 'sourceRevision': 'sha256:' + hashlib.sha256(canonical).hexdigest(),
              'revisionScope': 'ALLOWLISTED_CODE_TESTS_CONFIGURATION_SCRIPTS_EXCLUDING_DOCUMENTATION',
              'fileCount': len(files), 'bytes': sum(f['bytes'] for f in files), 'files': files,
              'secretPreflight': 'NO_MATCHES_IN_LIMITED_PATTERNS_NOT_A_SECURITY_CERTIFICATION'}
    if args.index:
        print(json.dumps({'sourceRevision': report['sourceRevision'], 'files': [
            {key: value for key, value in item.items() if key != 'sha256'} for item in core]}))
    elif args.upload_entries:
        entries = []
        for item in core:
            data = (ROOT / item['path']).read_bytes()
            entries.append({'path': item['path'], 'mode': item['mode'], 'type': 'blob',
                            **({'content': data.decode()} if item['text'] else
                               {'base64': base64.b64encode(data).decode()})})
        print(json.dumps({'manifest': report, 'entries': entries}))
    elif args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({key: value for key, value in report.items() if key != 'files'}))
    else:
        print(json.dumps(report, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
