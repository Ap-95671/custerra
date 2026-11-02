# Public source release

The owner approved Apache-2.0 for owner-controlled application code and accepted existing Git attribution metadata on 2 November 2026. See [licensing scope](../LICENSING.md) for separate dataset, third-party and brand rights.

The bounded review examined current source, all ten advertised refs (19 reachable commits before this documentation change), 130 retained Actions job logs and 80 artifacts containing 1,231 files. Redacted Gitleaks scans found one historical/current candidate, manually classified as the non-secret browser-storage namespace `custerra.journey-copies.v1`; retained logs/artifacts had no scanner candidates. These checks do not certify absence of every possible secret, and deleted/unadvertised refs or external caches are outside scope. No private production database export or credential was intentionally included.

Known dependency and container findings remain open in the engineering workflow. They were assessed separately for source-publication relevance and production-deployment acceptance. No vulnerability suppression, severity downgrade, forced dependency override or production-security signoff is implied. The existing product README distinguishes V2 candidate capabilities from the earlier default-branch runtime and keeps scientific limitations explicit.

This release changes repository licensing, documentation and visibility. It does not authorize an application deploy, restart, database operation, paid service, provider entitlement or patent-related research disclosure. Documentation commits carry `[skip ci] [skip render]` to avoid unnecessary cloud builds and Render auto-deploys. The live Custerra services and their configuration remain outside this source-release change.
