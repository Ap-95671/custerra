# Contributing to Custerra

Custerra is in development; production release acceptance remains open. Owner-controlled application code uses Apache-2.0; review [licensing scope](LICENSING.md). Discuss substantial work in an issue before writing it. Submit only contributions you have the right to provide under the applicable license and retain third-party provenance.

## Set up the project

Follow [getting started](docs/getting-started.md). Use Java 21, Node.js 24 and a dedicated PostgreSQL 17 database. Python 3.14 is needed only for the separate research service. Never point tests, fixtures or recovery scripts at production data.

## Make a focused change

1. Start a branch from the agreed target. Release-candidate work may target a candidate branch rather than `main`.
2. Link an existing issue, or open a reproducible issue describing the problem first.
3. Keep controllers thin, return DTOs and put domain rules in services. Validate external input and preserve account ownership and CSRF boundaries.
4. Add a new Flyway migration for a schema change. Never edit an applied migration or enable automatic destructive schema changes.
5. Preserve unavailable, partial, empty and UNKNOWN states, source attribution and timestamps. Do not turn missing evidence into a safety claim.
6. Update the relevant feature documentation and meaningful regression coverage. Keep generated files, credentials, private locations and database exports out of commits.

## Verify the change

Use the commands in [verification](docs/verification.md). Run the checks relevant to your change and report actual results and skips. Database integration tests require an isolated empty test database. Browser fixtures do not establish physical-device push delivery or live-provider acceptance.

Documentation-only changes should have valid links and accurate screenshots. Do not rerun expensive cloud/container workflows just to update prose. No paid infrastructure or third-party bot is authorized by a contribution.

## Open a pull request

Explain the concrete problem, resulting behavior, relevant issue and validation. Include authentic screenshots for visible changes, using synthetic accounts/public test locations. State compatibility, migration and security implications. Do not merge or deploy to production as part of preparing a PR.

## Bugs and research proposals

Use the issue templates. A bug needs reproducible steps and expected/observed behavior. Research proposals need a bounded question, dataset rights, leakage controls, baselines and acceptance criteria; they must distinguish experimental results from operational risk claims.

## Security and conduct

Follow [SECURITY.md](SECURITY.md) for sensitive findings and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) when collaborating. Do not post credentials, private journeys or vulnerability exploits in public issues. Private security reports use GitHub’s repository reporting flow when available. Private conduct reporting remains to be designated; do not post sensitive conduct reports publicly.
