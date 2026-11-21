import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('release_scope', Path(__file__).resolve().parents[1] / 'release-source-manifest.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseScopeTests(unittest.TestCase):
    def test_acceptance_reports_stay_local_including_machine_readable_evidence(self):
        for path in ['docs/milestone-26.md', 'docs/v2-external-acceptance/evidence/state.json',
                     'research/results.json', 'research/model-card.md', 'README.md']:
            self.assertFalse(release.publishable(Path(path)), path)

    def test_runtime_code_dependencies_and_test_fixtures_are_publishable(self):
        for path in ['backend/pom.xml', 'frontend/package-lock.json', 'ml-service/requirements.txt',
                     'backend/src/test/resources/fixture.json', 'scripts/tests/test_release_scope.py']:
            self.assertTrue(release.publishable(Path(path)), path)


if __name__ == '__main__':
    unittest.main()
