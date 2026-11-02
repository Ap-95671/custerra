# Verification and release status

Baseline application source: `9d044676b7abda01b68024ba9b12aadc9e323596`, November 1, 2026. Documentation changes do not alter that tested application implementation. No fabricated coverage percentage or passing overall CI badge is used.

## Actual results

| Check | Result |
| --- | --- |
| Hosted Java/PostgreSQL tests | 205 passed, 3 opt-in live tests skipped, zero failures/errors |
| Hosted frontend | Lint, build, typecheck, 27 unit tests and 26 browser tests pass |
| Hosted research | 5 tests pass; no operational prediction claim |
| Script tests | 12 pass |
| Monitoring-rule fixtures | Six rules and behavior cases pass; no deployed collector/receiver acceptance |
| Actual isolated HTTPS staging | 40 passed / 1 failed (weather upstream 502) |
| Focused actual route-progress check | Exact remaining geometry suffix verified on synthetic progress |
| Actual staging browser | Public map, generated journey save, offline-copy read while connected and sign-out cleanup pass |
| Strict security gates | FAIL: unresolved development dependency and container findings |

[Application workflow](https://github.com/Ap-95671/custerra/actions/runs/41329771068) · [Monitoring-rule workflow](https://github.com/Ap-95671/custerra/actions/runs/41329770686) · [Technical draft PR](https://github.com/Ap-95671/custerra/pull/1).

The staging database is independent and contains synthetic test data. These results do not establish positive-event notice generation, actual disconnected-browser behavior, elapsed idle-session expiry, physical-device push receipt/display, live FIRMS, live database preservation, compatible cross-version rollback or independent certification.

## Backend

```sh
mvn -f backend/pom.xml verify
```

Use JDK 21. Ordinary tests need no live provider or database. For integration tests, supply a dedicated empty database through `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`, then:

```sh
CUSTERRA_DATABASE_TESTS=true mvn -f backend/pom.xml verify
```

Never use production. Load fixtures additionally require `CUSTERRA_LOAD_TESTS=true` and a loopback database whose name matches the fixture safeguards. Upgrade fixtures require `TEST_DB_UPGRADE_URL` pointing to a new empty `custerra_upgrade_*` database. Inspect the fixture safeguards before opting in. A successful unit run with skipped integration tests must be reported as such.

## Frontend

```sh
cd frontend
npm ci
npm run lint
npm test
npm run build
npm run typecheck
npx playwright install chromium
npm run test:e2e
```

Browser fixtures intercept providers and map tiles. The test server uses port 3000. They do not demonstrate physical-device notifications or live-provider reliability.

## Research and scripts

With the separate Python research environment installed:

```sh
cd ml-service
python -m pytest -q
```

From the repository root:

```sh
python3 -m unittest discover -s scripts/tests -v
```

See [research reproduction](ml.md) for fixed dataset hashes, holdouts and artifacts. Recovery tests use synthetic data and do not create a usable backup of the live database.

## CI and costs

The application workflow runs backend/database, frontend, research, container and strict advisory checks with read-only default permissions. Scanning retains findings; failures are not waived. Fork code must never receive production secrets or privileged deployment credentials.

Documentation-only portfolio commits use `[skip ci]` to avoid repeating cloud builds for unchanged application code. This does not create a passing check or waive security gates; the recorded baseline remains failed overall. No paid runner, subscription, bot or infrastructure is required by this documentation work.
