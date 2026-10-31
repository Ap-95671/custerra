# Custerra

Custerra is a natural-hazard intelligence and travel-risk platform in early development. Its intended pipeline is **data sources → normalized hazards → risk assessment → explanation → map → monitoring**. The backend supports stored-hazard reads and live nearby multi-hazard evidence through USGS, NASA FIRMS (requires a key), and GDACS. An experimental recent-earthquake activity assessment is available; persistent ingestion and comprehensive location-risk modeling remain planned.

## Current status

Milestones 12–18 and the Milestone 20 API foundation are implemented and verified. Milestone 19 is verified locally and deployed as a free demo at [custerra-demo.onrender.com](https://custerra-demo.onrender.com). Hosted acceptance passed on October 30, 2026; continuous production hosting remains outside the selected free-demo scope. Live FIRMS verification remains deferred:

- Repository audit and preservation of the existing feature scaffold and `com.nci.backend` package.
- Java 21 / Spring Boot 4.0.7 / Maven API with Web MVC, Jakarta Validation, JPA and PostgreSQL.
- Versioned health endpoint, response DTO, centralized MVC error responses, and explicit CORS origins.
- Environment-based database credentials, Flyway schema migrations and Hibernate schema validation; no seed execution.
- Hazard entity, repository, service and DTOs with eleven hazard categories and explicit source provenance.
- Paginated hazard list with type/severity filters and lookup by UUID.
- USGS provider adapter with provider-neutral observations, source attribution, bounded responses and timeouts.
- Live nearby earthquake search with coordinate/radius validation, time-window bounds, distances and truncation metadata.
- Explainable location-assessment API with a replaceable earthquake strategy, evidence contributions, model version and explicit unknown coverage.
- Responsive Next.js/React map interface with coordinate entry, map selection, earthquake markers/popups, activity context and source evidence.
- Loading/error/empty states, layer toggle, abortable requests, runtime API validation and browser tests.
- Open-Meteo modeled weather context with temperature, rain, precipitation, wind, freshness and source attribution.
- Reproducible historical earthquake snapshots with observed annual frequency.
- Route geometry assessment, saved journeys, opt-in scheduled checks and deduplicated in-app notices.
- Explicit offline journey copies with a standalone reader, expiry, source timestamps and local removal.
- Account sessions, CSRF protection and per-account ownership of journeys and history.
- Owned notification inbox, eligibility preferences and a queued web-push adapter with recorded attempts and bounded retries.
- Separate reproducible historical research pipeline with model provenance, evaluation, calibration analysis and private inference.
- Container deployment, private operational metrics/provider status, readiness checks and bounded request limits.
- Separate B2B API credentials, ownership, shared quotas, usage audit and OpenAPI documentation.
- Unit/MVC/provider/risk tests plus opt-in PostgreSQL migration and persistence tests.

Milestone 12 adds NASA FIRMS thermal-anomaly observations, GDACS disaster advisories, provider/hazard filters, isolated source failures, per-source coverage, provenance, and three independent map layers. Live FIRMS verification remains pending because no map key is configured; this is not claimed as verified live service. See the [milestone log](docs/milestones.md).

Planned: shared catalog ingestion, additional providers, validated risk modeling, generated-route journey saving and broader offline coverage. Operational disaster prediction is not implemented; a separate historical research classifier is explicitly experimental. Web push passed a real provider/browser delivery smoke and remains opt-in. See [notifications](docs/notifications.md). Existing empty files represent planned structure only.

## Repository

| Directory | Status |
| --- | --- |
| `backend/` | Hazards, risk indicators, routes, owned journeys/history, monitoring and notifications |
| `frontend/` | Next.js / React / Leaflet explorer, routes, accounts, notifications and offline reader |
| `database/` | Empty utility/seed placeholders; active migrations live under `backend/src/main/resources/db/migration` |
| `ml-service/` | Historical research classifier, reproducible evaluation and private inference; no live risk integration |
| `infrastructure/` | Verified local container stack and hosted deployment templates |
| `docs/` | Feature contracts, architecture, provider limitations and milestone verification |

## Local setup

Install JDK 21, Maven 3.6.3+ and PostgreSQL. Ensure `mvn -version` reports the intended JDK (`JAVA_HOME` controls Maven's runtime). No Maven wrapper existed in the scaffold; use installed Maven.

Using your PostgreSQL administrator connection, create a local role and database once:

```sql
CREATE ROLE custerra LOGIN;
\password custerra
CREATE DATABASE custerra OWNER custerra;
```

The `\password` command runs inside `psql` and prompts for the password without putting it in shell history. Do not run these commands against an unrelated production database.

From the repository root:

```sh
cp .env.example .env
# Edit .env to match your local PostgreSQL credentials.
# Quote values containing shell special characters; .env is sourced by the shell.
set -a
. ./.env
set +a
mvn -f backend/pom.xml spring-boot:run
```

Spring Boot reads exported environment variables; it does not automatically read `.env`. Only source a file you trust. `.env` is ignored by Git.

| Variable | Default / requirement |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/custerra` |
| `DB_USERNAME` | Required |
| `DB_PASSWORD` | Required |
| `SERVER_PORT` | `8080` |
| `SERVER_ADDRESS` | `127.0.0.1`; local-only default for shared installation data |
| `MONITORING_ENABLED` | `false`; opt in to scheduled ACTIVE-journey checks |
| `MONITORING_INTERVAL_MS` | `300000`; 60,000–86,400,000 ms |
| `NOTIFICATIONS_ENABLED` | `false`; opt in to queued web-push dispatch |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | Blank; configure a matching key pair and real contact URI together; see notifications setup |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000`; comma-separated exact origins |
| `FIRMS_MAP_KEY` | Empty by default; requires a NASA FIRMS key for live thermal observations |
| `FIRMS_CONNECT_TIMEOUT_MS` / `FIRMS_READ_TIMEOUT_MS` | `3000` / `10000` |
| `GDACS_ENABLED` | `true`; disabling reports unavailable coverage |
| `GDACS_CONNECT_TIMEOUT_MS` / `GDACS_READ_TIMEOUT_MS` | `3000` / `10000` |
| `USGS_QUERY_URL` | Official HTTPS USGS catalog query endpoint (see `.env.example`) |
| `USGS_CONNECT_TIMEOUT_MS` | `3000`; configurable 100–30,000 ms |
| `USGS_READ_TIMEOUT_MS` | `10000`; configurable 100–30,000 ms |
| `OPEN_METEO_URL` | `https://api.open-meteo.com/v1/forecast`; free service for non-commercial use |
| `OPEN_METEO_CONNECT_TIMEOUT_MS` | `3000`; configurable 100–30,000 ms |
| `OPEN_METEO_READ_TIMEOUT_MS` | `10000`; configurable 100–30,000 ms |

PostgreSQL must be reachable for normal startup. JPA uses `ddl-auto: validate`, and SQL initialization is disabled. Flyway applies versioned migrations before Hibernate validates the schema: V1 creates hazards; V2 adds historical snapshots, journeys and monitoring evidence; V3 adds the ADVISORY information type; V4 adds multi-source monitoring checkpoints; V5 adds account ownership; V6 adds notification preferences, subscriptions, queue and attempt audit; V7 adds API-client organizations, credentials and usage quotas. The database user must have schema migration privileges. Flyway clean and automatic baselining are disabled. Use a dedicated Custerra database; an unrelated nonempty schema will not be silently adopted. Never edit an applied migration; add a new version for future changes.

## API

```sh
curl -i http://localhost:8080/api/v1/health
```

```json
{"status":"UP","service":"Custerra API"}
```

This endpoint reports API liveness, not ongoing database/provider readiness or hazard safety. Stored hazards are available through `GET /api/v1/hazards` and `GET /api/v1/hazards/{id}`. A new database returns an empty page because live nearby observations are not automatically persisted. Empty results do not establish that a location is safe. Try a live nearby earthquake query:

```sh
curl 'http://localhost:8080/api/v1/hazards/nearby?latitude=32.24&longitude=77.19&radiusKm=100&days=7'
```

The experimental assessment uses the same query parameters:

```sh
curl 'http://localhost:8080/api/v1/risk/location?latitude=32.24&longitude=77.19&radiusKm=100&days=7'
```

Overall risk stays UNKNOWN. The original endpoint retains the earthquake indicator; `/api/v1/risk/multi-hazard` adds experimental thermal proximity, source completeness and explanatory wind context. Neither index is a calibrated disaster-risk prediction. See the [risk model](docs/risk-model.md) and [API contract](docs/api-design.md).

Current modeled weather for the selected coordinates:

```sh
curl 'http://localhost:8080/api/v1/weather/location?latitude=32.24&longitude=77.19'
```

Weather is separate context and does not change risk scores. Rain and precipitation are totals over the returned provider interval, not daily totals. Missing values remain unavailable. The default Open-Meteo API is for non-commercial use; its data requires CC BY 4.0 attribution. See [provider terms and configuration](docs/data-sources.md).

## Verification

```sh
mvn -f backend/pom.xml verify
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```

`verify` runs unit/MVC tests and packages the executable JAR. PostgreSQL integration tests are skipped by default; ordinary tests need neither PostgreSQL nor external APIs; dependency downloads need network access on the first build. The JAR needs the exported database environment above. Frontend commands and configuration are documented below.

To run the twelve PostgreSQL tests as well, create a **dedicated empty test database** and export `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`. Then run:

```sh
CUSTERRA_DATABASE_TESTS=true mvn -f backend/pom.xml verify
```

The test configuration requires those three variables and does not fall back to the normal database. Flyway creates the schema; test event inserts roll back after each test. No test fixtures are shipped as production seed data.

## Architecture and roadmap

See the [repository audit](docs/repository-audit.md), [architecture decisions](docs/architecture.md) and [API contract](docs/api-design.md). Milestones 8–10 add [historical exposure, routes and journey monitoring](docs/history-and-journeys.md). Milestone 11 adds [offline journey copies](docs/offline.md). Milestone 12 introduces multi-hazard evidence; Milestones 13–20 cover routing, risk strategies, monitoring, accounts, notifications, research, deployment and B2B APIs in that order. The [risk models](docs/risk-model.md) are experimental earthquake-activity and thermal-proximity indicators; overall risk remains UNKNOWN. Risk strategies must be deterministic, explainable and replaceable; observed events, official warnings and modeled risk must remain distinct. Provider source, attribution, licensing and freshness are documented for USGS and Open-Meteo and must be retained for future integrations. USGS is connected for on-demand earthquake queries; see [data sources](docs/data-sources.md).

Custerra is not an official emergency-warning authority. Future assessments will have uncertainty and limited coverage; local government and emergency instructions take precedence. No earthquake prediction or scientifically validated risk score is claimed.


## Frontend setup

Use Node.js 24 and npm (the repository now includes `frontend/package-lock.json`). Start PostgreSQL and the backend as described above, then in a second terminal:

```sh
cd frontend
npm ci
# Optional: copy .env.example to .env.local and adjust the API URL.
npm run dev
```

Open [Custerra locally](http://localhost:3000). Enter coordinates, choose a quick location or click the map. No location permissions or map API key are required. Earthquake observations and modeled weather context are connected. Use `/history` for saved catalog-frequency snapshots and `/routes` for supplied waypoint geometry and saved journeys. Place-name search and driving route previews are available on `/routes`; manual waypoint journeys remain available. Offline maps remain planned. FIRMS and GDACS layers now expose their own coverage and limitations. Select a saved journey and choose **Save offline copy** for a read-only browser copy, then open **Offline copies** before departure.

`NEXT_PUBLIC_API_BASE_URL` defaults to `http://localhost:8080`. The browser origin must match the backend's `CORS_ALLOWED_ORIGINS`; use `localhost:3000` by default, or explicitly add the origin you use. Next.js public environment values are bundled at build time: rebuild after changing them, and never put secrets in these variables.

```sh
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:e2e
# Production server after building:
npm start
```

The standard service and browser tests use fixtures, not live provider APIs. The browser suite intercepts map-tile requests too. It starts its own production server on port 3000; stop other frontend servers before running it. See [frontend architecture](docs/frontend.md) for request behavior, map-provider settings and current limitations.


Saved journeys and historical snapshots belong to signed-in users. Use `/register` or `/login`; see [authentication and legacy-data migration](docs/authentication.md). Keep the default loopback API binding for local use. Scheduled checks are disabled by default and are clearly reported in the journey UI; see the [Milestones 8–10 guide](docs/history-and-journeys.md) for activation, API bodies, storage, limitations and provider behavior.

Journey monitoring now supports multiple providers with independent baselines, meaningful revision notices and explicit partial coverage. Current full-route checks use stored waypoint segments; generated road-route saving and live GPS remain unimplemented. See [monitoring semantics](docs/history-and-journeys.md).

## Research, deployment and external API

See the [research model card](docs/ml.md), [deployment runbook](docs/deployment.md), [B2B API guide](docs/b2b-api.md) and [OpenAPI contract](docs/b2b-openapi.yaml). Local container smoke tests passed for all three services. The user selected a free Render demo. Its free PostgreSQL instance is available, and the combined app container passed a 512 MB local rehearsal. The [hosted demo](https://custerra-demo.onrender.com) passed verified database TLS, migrations V1–V7, readiness, sign-in/session/CSRF, notification inbox and API-key lifecycle checks on October 30, 2026. The database expires **November 24, 2026**. Idle startup can take several minutes; background monitoring and outbound dispatch are disabled. During hosted acceptance Open-Meteo returned HTTP 502, so weather context is currently unavailable. See the [deployment record](docs/deployment-proposal.md). The research model remains unsuitable for operational hazard decisions.
