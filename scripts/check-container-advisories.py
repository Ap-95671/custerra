#!/usr/bin/env python3
"""Fail closed on Trivy HIGH/CRITICAL findings, including unfixed packages.

This gate does not assert exploitability and has no ignore list. Keep the raw
scanner artifact alongside the summary for distribution-specific human review.
"""
import argparse
import json
from pathlib import Path


def summarize(report):
    if (not isinstance(report, dict) or report.get('SchemaVersion') != 2
            or not isinstance(report.get('Metadata'), dict)
            or not report['Metadata'].get('ImageID')
            or not isinstance(report.get('Results'), list) or not report['Results']):
        raise ValueError('Missing or unsupported Trivy image report; acceptance denied.')
    findings = []
    for result in report['Results']:
        if not isinstance(result, dict) or not result.get('Target'):
            raise ValueError('Invalid scanner target; acceptance denied.')
        vulnerabilities = result.get('Vulnerabilities', [])
        if not isinstance(vulnerabilities, list):
            raise ValueError('Invalid vulnerability list; acceptance denied.')
        for item in vulnerabilities:
            if (not isinstance(item, dict)
                    or not all(item.get(key) for key in ('VulnerabilityID', 'PkgName', 'InstalledVersion', 'Severity'))):
                raise ValueError('Incomplete vulnerability record; acceptance denied.')
            if item['Severity'] not in {'UNKNOWN', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'}:
                raise ValueError('Unsupported severity; acceptance denied.')
            if item['Severity'] in {'HIGH', 'CRITICAL'}:
                findings.append({
                    'id': item['VulnerabilityID'], 'package': item['PkgName'],
                    'installed': item['InstalledVersion'], 'fixed': item.get('FixedVersion') or None,
                    'severity': item['Severity'], 'target': result['Target'],
                })
    return {'scope': 'TRIVY_VERSION_MATCHING_NOT_EXPLOITABILITY_OR_INDEPENDENT_AUDIT',
            'imageId': report['Metadata']['ImageID'],
            'status': 'FAILED' if findings else 'VERIFIED WITH LIMITATIONS',
            'highCriticalOccurrences': len(findings),
            'upstreamFixNotListed': sum(item['fixed'] is None for item in findings),
            'findings': findings}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = summarize(json.loads(args.report.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key != 'findings'}))
    return 1 if result['highCriticalOccurrences'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
