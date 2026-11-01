import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('container_gate', Path(__file__).resolve().parents[1] / 'check-container-advisories.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ContainerAdvisoryTests(unittest.TestCase):
    def setUp(self):
        self.report = {'SchemaVersion': 2, 'Metadata': {'ImageID': 'sha256:fixture'},
                       'Results': [{'Target': 'fixture (debian)', 'Vulnerabilities': []}]}

    def test_unfixed_high_and_critical_still_fail(self):
        for severity in ('HIGH', 'CRITICAL'):
            with self.subTest(severity=severity):
                self.report['Results'][0]['Vulnerabilities'] = [{
                    'VulnerabilityID': 'CVE-TEST', 'PkgName': 'fixture',
                    'InstalledVersion': '1.0', 'Severity': severity}]
                result = gate.summarize(self.report)
                self.assertEqual(result['status'], 'FAILED')
                self.assertEqual(result['upstreamFixNotListed'], 1)

    def test_fixed_high_still_fails_until_installed_package_changes(self):
        self.report['Results'][0]['Vulnerabilities'] = [{
            'VulnerabilityID': 'CVE-TEST', 'PkgName': 'fixture', 'InstalledVersion': '1.0',
            'Severity': 'HIGH', 'FixedVersion': '1.1'}]
        self.assertEqual(gate.summarize(self.report)['highCriticalOccurrences'], 1)

    def test_clean_report_has_explicit_limited_scope(self):
        self.assertEqual(gate.summarize(self.report)['status'], 'VERIFIED WITH LIMITATIONS')

    def test_missing_or_malformed_scan_cannot_pass(self):
        malformed = [None, {}, {**self.report, 'Results': []}, {**self.report, 'SchemaVersion': 1}]
        for mutation in ({'Vulnerabilities': None}, {'Vulnerabilities': [{}]},
                         {'Vulnerabilities': [{'VulnerabilityID': 'CVE-TEST', 'PkgName': 'fixture',
                                              'InstalledVersion': '1.0', 'Severity': 'unexpected'}]}):
            report = copy.deepcopy(self.report)
            report['Results'][0].update(mutation)
            malformed.append(report)
        for report in malformed:
            with self.subTest(report=report), self.assertRaises(ValueError):
                gate.summarize(report)
