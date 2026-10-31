# Free demo deployment

The user approved private `Ap-95671/custerra`, uploading source/tests/docs/configuration and attributed research data, and granting Render access only to that repository. The user chose a **free demo**, declining the $52.50/month paid proposal. Paid resources or upgrades are not authorized.

## Selected configuration

- One free Render web service in Singapore, built with `infrastructure/Dockerfile.demo`. Next.js and Spring Boot retain their separate processes; Spring binds only to loopback. The supervisor waits for database readiness before starting the web process and stops both on termination or either process exiting.
- One free PostgreSQL 17 database, 1 GB storage: `custerra-demo-db`. Created and confirmed Available in Render. The dashboard lists expiry as **November 24, 2026**. No managed backups are supplied on this plan.
- `render.yaml` selects only free plans. `infrastructure/render-paid.yaml` is an optional, unapplied reference; do not apply it without separate paid-hosting approval.
- Root Blueprint requires the database's external TLS hostname as `DB_HOST`. Render's internal database hostname uses a self-signed certificate and cannot satisfy `verify-full`. Java's standard trust store validates the external endpoint; deployment must fail if TLS validation fails.
- Database external traffic should be limited to the demo service's observed outbound CIDRs. The user approved the two shared Render Singapore egress ranges, `74.220.52.0/24` and `74.220.60.0/24`, after their shared nature was explained. Both were saved on October 30 and recorded in `render.yaml`; no allow-everywhere database rule remains. The workspace-level permissive rule is unchanged; database-level restrictions narrow it. Password authentication and verified TLS remain required.
- No payment method was present in the checked workspace. Do not add one or upgrade. Free web usage shares the workspace's 750 monthly instance hours; bandwidth/build limits may suspend service. Existing AbhiAI resources are unrelated and must not be changed.
- Research hosting, background monitoring and outbound dispatch remain disabled. The demo UI explains sleep and data-expiry limitations. It is not a continuously operating warning system or a production release.

## Publication boundary

Upload project source, tests, attributed research data/artifacts, documentation and deployment configuration. Exclude `.env`, `.env.push`, credentials, logs, database contents and generated dependencies. Private `Ap-95671/custerra` contains the reviewed 378-file source publication (initial implementation commit `5b75e25`). Render access was added for Custerra while preserving its pre-existing AbhiAI access. The free Blueprint `custerra-free-demo` and web service `custerra-demo` have been created.

## Verification

The combined container built and passed a local rehearsal under a strict 512 MB memory / 0.5 CPU limit: migrations/readiness, session/CSRF, notification inbox, API-key issuance/usage/revocation and logout. Observed memory was approximately 443 MiB with no OOM event; this is a smoke result, not a load-capacity guarantee. Free and optional paid Blueprints pass Render's JSON schema.

Hosted acceptance passed on October 30, 2026 at [custerra-demo.onrender.com](https://custerra-demo.onrender.com), source commit `88bfc64`, Render deploy `dep-dbiaeqbncjis73aq7rag`. The first deploy failed while external database access was blocked; the approved network correction resolved it. Startup logs confirm verified external PostgreSQL TLS, migrations V1–V7 and both app processes ready. Actual HTTPS smoke checks passed readiness, registration/login, session/CSRF, inbox, hidden operations, API-key issuance/accounting/revocation and logout. Browser inspection confirmed the map interface and demo notice. Cold starts can take several minutes (Spring process startup took approximately 167 seconds). Production backup/recovery, metrics collection/alerts and continuous monitoring remain outside the free-demo acceptance. Live FIRMS remains deferred.

References: [Render free service limits](https://render.com/docs/free), [database connections](https://render.com/docs/postgresql-creating-connecting), [Blueprint reference](https://render.com/docs/blueprint-spec), [PostgreSQL JDBC TLS](https://jdbc.postgresql.org/documentation/ssl/).

The live Manali query retrieved USGS/GDACS responses with explicit coverage. Open-Meteo returned upstream HTTP 502 on the initial attempt and retry; weather context displayed unavailable. FIRMS remained NOT_CONFIGURED as expected. These provider gaps are not counted as successful live weather/FIRMS verification.
