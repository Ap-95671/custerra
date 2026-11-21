#!/usr/bin/env python3
"""Hosted disposable image check; no provider calls, keys or live database."""
import argparse
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--component', choices=('frontend', 'demo', 'ml'), required=True)
    parser.add_argument('--image', required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    if args.component == 'ml':
        interpreter = 'python'
        program = '''
import importlib.util
from fastapi.testclient import TestClient
from app.main import app
for name in ('pip', 'setuptools', 'wheel'):
    assert importlib.util.find_spec(name) is None, name
client = TestClient(app)
health = client.get('/health')
assert health.status_code == 200 and health.json()['mode'] == 'RESEARCH_ONLY'
prediction = client.post('/v1/research/fire-label', json={
    'region':'BEJAIA', 'observedDate':'2012-07-01', 'temperatureC':30,
    'relativeHumidityPct':60, 'windKmh':15, 'rainMm':0})
assert prediction.status_code == 200
assert prediction.json()['informationType'] == 'EXPERIMENTAL_PREDICTION'
assert prediction.json()['confidence'] == 'UNKNOWN'
assert client.post('/v1/research/fire-label', json={}).status_code == 422
'''
        command = [interpreter, '-B', '-c', program]
    else:
        interpreter = 'node'
        program = '''
const assert = require('node:assert/strict');
const fs = require('node:fs');
for (const path of ['/usr/local/lib/node_modules/npm', '/usr/local/bin/npm',
 '/usr/local/bin/npx', '/usr/local/bin/yarn', '/usr/local/bin/yarnpkg']) {
 assert.equal(fs.existsSync(path), false, path);
}
assert.equal(fs.readdirSync('/opt').some(name => name.startsWith('yarn-')), false);
assert.equal(fs.existsSync('/app/server.js'), true);
require.resolve('next');
'''
        command = [interpreter, '-e', program]
    subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--read-only',
        '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges',
        '--entrypoint', interpreter, args.image, *command[1:]], check=True, timeout=90)
    report = {'scope': 'HOSTED_DISPOSABLE_IMAGE_NO_NETWORK', 'component': args.component,
              'buildOnlyToolsAbsent': True, 'runtimeImportOrResearchHttp': 'PASS',
              'productionAcceptance': False}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
