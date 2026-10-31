# Deployment and reliability — Milestone 19

The selected hosted target is a **free demo on Render**, as requested by the user. It combines the existing Next.js and Spring Boot processes in one 512 MB container, with Spring bound only to loopback, and uses free managed PostgreSQL. The browser still makes same-origin `/api/v1` calls. Research remains separate and is not publicly deployed.

`render.yaml` selects free resources. `infrastructure/render-paid.yaml` preserves the optional separate web/private-API architecture, but paid hosting was declined and must not be applied without approval. A free PostgreSQL 17 instance is Available in Singapore and expires November 24, 2026. The app is live at [custerra-demo.onrender.com](https://custerra-demo.onrender.com); hosted acceptance passed on October 30, 2026. See the [current deployment record](deployment-proposal.md) for evidence and limits.

Free Render apps sleep when idle; PostgreSQL expires after 30 days and has no managed backups. The demo is unsuitable for continuous monitoring or durable user records. Background monitoring and outbound dispatch are disabled, and the UI carries a temporary-demo notice. Local and future production architecture remains available separately.

## Environments and startup

Development uses the existing local defaults. Tests use a dedicated explicitly selected PostgreSQL database. Production sets `SPRING_PROFILES_ACTIVE=production`, verified PostgreSQL TLS (`sslmode=verify-full`), HTTPS CORS origins, secure cookies and an independently generated `OPS_TOKEN` of at least 32 characters. Invalid production configuration fails startup. Never commit or print tokens/passwords.

Docker builds use the existing Java 21/Maven and Next.js stacks. Multi-stage images run non-root. Next standalone output includes static/public assets; builds set an empty `NEXT_PUBLIC_API_BASE_URL` for same-origin requests. Local non-container development retains localhost:8080 by default. `BACKEND_INTERNAL_URL` or deployment-provided `BACKEND_HOST` is server-only. Request bodies through the proxy are capped at 1 MiB; headers are allowlisted, redirects are not followed and upstream errors are sanitized.

The demo supervisor takes `CORS_ALLOWED_ORIGINS` from Render's actual `RENDER_EXTERNAL_URL`; the optional separate deployment requires an explicit HTTPS web origin. Database credentials are wired from managed PostgreSQL, and the backend entrypoint builds the JDBC URL without logging it. Set `DB_HOST` to the external TLS hostname and allowlist the app's observed outbound CIDRs. Render internal database hosts use self-signed certificates and cannot meet the production `verify-full` guard. The entrypoint uses the JVM trust store through PostgreSQL's `DefaultJavaSSLFactory`; do not disable certificate verification to make startup succeed. Push and monitoring stay off until deliberately configured. VAPID and FIRMS credentials remain external and separate from operations credentials.

Flyway applies additive migrations before Hibernate validation. The migration-capable database role is currently also the application role; separate migration/runtime grants before larger-scale operation. A failed migration must fail deployment; do not enable Flyway clean or silently baseline an unrelated database. Shut down gracefully with a 30-second application grace interval and at least 40 seconds at the container/orchestrator boundary. Active servlet sessions expire on a restart: use one backend replica until shared session storage is introduced.

## Local container rehearsal

Set local test `DB_PASSWORD` and `OPS_TOKEN` in the environment, then:

```sh
docker compose -p custerra-verification -f infrastructure/docker-compose.yml up --build -d
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:3000/health
curl --fail http://localhost:3000/api/v1/health
```

Compose's database is local PostgreSQL in a dedicated named volume; it is not the production managed instance. The optional `research` profile starts only the separate experimental API. `docker compose ... down` retains the database volume. Do not use `down -v` against data that must be preserved.

## Operations

- `/actuator/health/liveness` checks process liveness; `/actuator/health/readiness` includes database connectivity. Neither reports hazard safety. Public health responses omit component details.
- `/actuator/prometheus` and `/api/internal/operations/providers` require `Authorization: Bearer <OPS_TOKEN>`. Consumer sessions cannot access them. The public web proxy only permits `/api/v1/` and does not proxy operations endpoints.
- Micrometer records HTTP request latency, provider latency/outcome, degraded source counts, journey-check/notification-job failures and push-attempt outcomes. Tags use fixed providers/statuses, never coordinates, endpoints, keys or account IDs. JVM/pool metrics are available through Prometheus.
- Provider status is last observed query status: HEALTHY, DEGRADED or UNAVAILABLE; unqueried sources are NOT_CHECKED and results older than 15 minutes become STALE. State resets on restart. It is not an independent uptime probe, and source completeness remains visible in consumer responses.
- Public expensive endpoints have a bounded in-process peer limiter (120 requests/minute by default, `PUBLIC_REQUESTS_PER_MINUTE`). Authentication retains its tighter limiter. The application does not trust user-supplied forwarded addresses. Behind the proxy, peers share the API limit; configure trusted edge per-client limits before public scale. This is a conservative aggregate safeguard, not distributed per-user metering.

Connect a private metrics scraper before release. Suggested initial alerts: readiness failure for 2 minutes; any job failure; push FAILED/UNCERTAIN growth; a queried critical source UNAVAILABLE or STALE; sustained 5xx or increasing API latency. Thresholds need traffic-based tuning. Logs intentionally omit credentials, coordinates and upstream exception bodies. Central log retention/export is an operator configuration, not supplied by writing a Dockerfile.

## Database, retention and recovery

The current pool limit is ten connections per process. Budget managed database connections for deployments, migrations and monitoring; avoid multiplying replicas before reviewing the budget. Owner/time and queue-due indexes bound routine notification reads and dispatch. Monitor storage, dead tuples, queue backlog and slow queries.

Before release, enable managed automated backups/PITR according to the purchased plan, record the retention window, and perform a restore to an isolated instance. Verify Flyway version, row counts, owner isolation and application readiness there. Proposed initial recovery objectives (not measured guarantees): RPO 24 hours, RTO 4 hours. Keep external encrypted backups if the business requires independence from the host.

Proposed retention: notification items/attempts 90 days, aggregate API usage 90 days, transient provider-health memory 15-minute freshness. Cleanup must be an explicitly scheduled, reviewed operation with backups; no surprise deletion job is enabled. Journey/history retention remains user-controlled. Do not roll back by editing applied migrations: deploy the prior compatible application image, then add a forward repair migration if required.

## Release acceptance still required

For the selected free demo, verify source repository access, HTTPS origin, database network rules/TLS/migrations, owned user workflows and API boundaries on the actual deployment. A future production release additionally needs durable hosting, managed backup restore, private metrics scrape/alerts, rate-limit tuning and hosted provider/push verification. No production SLA or managed-database success is claimed from local tests.

References: [Render Blueprint fields](https://render.com/docs/blueprint-spec), [Spring Boot operational endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Next standalone output](https://nextjs.org/docs/app/api-reference/config/next-config-js/output).

## Local verification result — 2026-10-25

Frontend, backend and research images built successfully. An isolated Compose database passed migrations through V7 and readiness. Actual HTTP checks passed registration/login through the proxy, session cookies/CSRF, the owned notification inbox, API-key issuance, admitted-request accounting, revocation and logout. The separate research container returned scoped experimental metadata over HTTP. Verification containers were stopped afterward; their dedicated test volume was retained. A separate live push smoke also passed provider acceptance and native Chrome receipt. The private source repository is published, Render access is configured and the free demo passed hosted acceptance on October 30, 2026. No paid resources were created. See the [deployment proposal](deployment-proposal.md).

## Free demo rehearsal — 2026-10-26

The combined container passed the same HTTP smoke under 512 MB memory and 0.5 CPU limits, with about 443 MiB observed memory and no OOM event. The API is not bound publicly. The supervisor gates web startup on database readiness and forwards shutdown to both processes. This small smoke does not establish concurrency capacity.

Use `CUSTERRA_SMOKE_READINESS_URL=http://127.0.0.1:3000/health python3 scripts/verify-local-deployment.py` for the combined local image. The separate Compose rehearsal retains the default direct readiness URL. `CUSTERRA_SMOKE_BASE_URL` can select an explicitly authorized hosted demo; the smoke creates a test account and revokes its test API key.

### Hosted demo acceptance — October 30, 2026

Render deploy `dep-dbiaeqbncjis73aq7rag` succeeded on source commit `88bfc649e5038e172e25d02da3db3daebbc4dcd1`. Startup logs show PostgreSQL 17.11 over the external hostname with `sslmode=verify-full` and the JVM trust store, seven applied Flyway migrations and both Spring Boot/Next.js ready. Spring process startup took approximately 167 seconds; allow several minutes after an idle spin-down.

The actual HTTPS deployment passed `scripts/verify-local-deployment.py`: readiness, registration/login, session and CSRF, owned inbox, hidden internal operations endpoint, API-key issuance, admitted-request accounting, revocation rejection and logout. No credentials were printed. A synthetic test account remains in this temporary database with its API key revoked. Browser inspection confirmed the map interface, source distinctions, UNKNOWN risk and the demo limitations notice.

Database inbound rules contain only the two user-approved shared Singapore Render ranges (`74.220.52.0/24`, `74.220.60.0/24`). They are shared regional addresses, not app-exclusive IPs; authentication and verified TLS remain required. The same rules are in the root Blueprint. Do not replace them with `0.0.0.0/0`.

This completes the selected free-demo acceptance. Protected metrics/provider diagnostics exist on the loopback backend and were tested locally; an external metrics collector/alert integration is not configured for this free demo. Durable backups, restore drills, continuous monitoring and operational model validation remain future production work. Live FIRMS verification remains explicitly deferred.

The live Manali query retrieved USGS/GDACS responses with explicit coverage. Open-Meteo returned upstream HTTP 502 on the initial attempt and retry; weather context displayed unavailable. FIRMS remained NOT_CONFIGURED as expected. These provider gaps are not counted as successful live weather/FIRMS verification.
