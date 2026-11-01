#!/usr/bin/env python3
"""Read-only OSV version matching for a Maven JSON tree and pinned Python list.

Sends public dependency coordinates/versions, not repository source or credentials.
This is a dated advisory lookup, not a security audit or exploitability analysis.
"""
import argparse
import datetime
import json
from pathlib import Path
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--maven-tree', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    packages = set()

    def visit(node):
        if node['groupId'] != 'com.nci':
            packages.add(('Maven', node['groupId'] + ':' + node['artifactId'], node['version']))
        for child in node.get('children', []):
            visit(child)

    visit(json.loads(args.maven_tree.read_text()))
    root = Path(__file__).resolve().parents[1]
    for line in (root / 'ml-service/requirements.txt').read_text().splitlines():
        if line and not line.startswith('#'):
            name, version = line.split('==')
            packages.add(('PyPI', name, version))
    ordered = sorted(packages)
    query = {'queries': [{'package': {'ecosystem': ecosystem, 'name': name}, 'version': version}
                         for ecosystem, name, version in ordered]}
    request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
                                     data=json.dumps(query).encode(),
                                     headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(request, timeout=45) as response:
        rows = json.load(response)['results']
    assert len(rows) == len(ordered), 'Incomplete advisory response'
    findings = [{'ecosystem': package[0], 'package': package[1], 'version': package[2],
                 'advisories': sorted({v['id'] for v in row.get('vulns', [])})}
                for package, row in zip(ordered, rows) if row.get('vulns')]
    report = {'checkedOn': datetime.datetime.now(datetime.timezone.utc).date().isoformat(), 'source': 'https://api.osv.dev/v1/querybatch',
              'scope': 'MAVEN_AND_PINNED_PYTHON_VERSION_MATCHING_ONLY',
              'packagesQueried': len(ordered), 'packagesWithAdvisoryMatches': len(findings),
              'findings': findings, 'securityAcceptance': 'REQUIRES_REVIEW'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'packagesQueried': len(ordered), 'packagesWithAdvisoryMatches': len(findings)}))


if __name__ == '__main__':
    main()
