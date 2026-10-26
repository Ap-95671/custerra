# Free demo deployment

The user approved private `Ap-95671/custerra`, uploading source/tests/docs/configuration and attributed research data, and granting Render access only to that repository. The user chose a **free demo**, declining the $52.50/month paid proposal. Paid resources or upgrades are not authorized.

## Selected configuration

- One free Render web service in Singapore, built with `infrastructure/Dockerfile.demo`. Next.js and Spring Boot retain their separate processes; Spring binds only to loopback. The supervisor waits for database readiness before starting the web process and stops both on termination or either process exiting.
- One free PostgreSQL 17 database, 1 GB storage: `custerra-demo-db`. Created and confirmed Available in Render. The dashboard lists expiry as **November 24, 2026**. No managed backups are supplied on this plan.
- `render.yaml` selects only free plans. `infrastructure/render-paid.yaml` is an optional, unapplied reference; do not apply it without separate paid-hosting approval.
- Root Blueprint requires the database's external TLS hostname as `DB_HOST`. Render's internal database hostname uses a self-signed certificate and cannot satisfy `verify-full`. Java's standard trust store validates the external endpoint; deployment must fail if TLS validation fails.
- Database external traffic should be limited to the demo service's observed outbound CIDRs. The user approved restricting the database to Custerra. The default allow-everywhere rule has been removed, and Render confirms external traffic is blocked. Add only the app's observed outbound CIDRs when provisioning it.
- No payment method was present in the checked workspace. Do not add one or upgrade. Free web usage shares the workspace's 750 monthly instance hours; bandwidth/build limits may suspend service. Existing AbhiAI resources are unrelated and must not be changed.
- Research hosting, background monitoring and outbound dispatch remain disabled. The demo UI explains sleep and data-expiry limitations. It is not a continuously operating warning system or a production release.

## Publication boundary

Upload project source, tests, attributed research data/artifacts, documentation and deployment configuration. Exclude `.env`, `.env.push`, credentials, logs, database contents and generated dependencies. Private `Ap-95671/custerra` has been created and connector write access verified. Source publication and Render access to that repository remain pending; Render currently sees only the unrelated AbhiAI repository.

## Verification

The combined container built and passed a local rehearsal under a strict 512 MB memory / 0.5 CPU limit: migrations/readiness, session/CSRF, notification inbox, API-key issuance/usage/revocation and logout. Observed memory was approximately 443 MiB with no OOM event; this is a smoke result, not a load-capacity guarantee. Free and optional paid Blueprints pass Render's JSON schema.

Remaining: source publication/access, app provisioning, approved database network rules, external database TLS, hosted migrations and browser/API acceptance. Production backup/recovery and continuous monitoring remain outside the free-demo acceptance.

References: [Render free service limits](https://render.com/docs/free), [database connections](https://render.com/docs/postgresql-creating-connecting), [Blueprint reference](https://render.com/docs/blueprint-spec), [PostgreSQL JDBC TLS](https://jdbc.postgresql.org/documentation/ssl/).
