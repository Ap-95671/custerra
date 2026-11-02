# Getting started

Use an isolated development database. No hosted provisioning, paid service, real notification or production-data access is required by this guide.

## Select the V2 candidate

The product preview documents the V2 candidate; the default-branch application runtime is still the earlier release. For the current candidate:

```sh
git clone --branch release/v2-candidate https://github.com/Ap-95671/custerra.git
cd custerra
```

No application deployment is performed by cloning or updating documentation.

## Backend and database

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

PostgreSQL must be reachable for normal startup. JPA uses `ddl-auto: validate`, and SQL initialization is disabled. Flyway applies versioned migrations before Hibernate validates the schema: V1 creates hazards; V2 adds historical snapshots, journeys and monitoring evidence; V3 adds the ADVISORY information type; V4 adds multi-source monitoring checkpoints; V5 adds account ownership; V6 adds notification preferences, subscriptions, queue and attempt audit; V7 adds API-client organizations, credentials and usage quotas; V8 adds generated geometry/lifecycle/progress; V9 adds source-scoped evidence revisions; V10 adds durable worker execution status; V11 adds API-key operation scopes. The database user must have schema migration privileges. Flyway clean and automatic baselining are disabled. Use a dedicated Custerra database; an unrelated nonempty schema will not be silently adopted. Never edit an applied migration; add a new version for future changes.


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

The standard service and browser tests use fixtures, not live provider APIs. The browser suite intercepts map-tile requests too. It starts its own production server on port 3000; stop other frontend servers before running it. See [frontend architecture](frontend.md) for request behavior, map-provider settings and current limitations.


## Next steps

See [verification](verification.md), [API contracts](api-design.md), [authentication](authentication.md), [generated journeys](history-and-journeys.md), [notifications](notifications.md), [offline copies](offline.md) and [deployment](deployment.md). Research has its own [setup and model card](ml.md).

Provider service terms apply independently of code licensing. Free defaults are for bounded, non-commercial testing where the provider permits it. No paid subscription is included or authorized.

## Troubleshooting

- Database connection or migration failures: check the dedicated local database, credentials and reachability; do not enable Flyway clean or automatic baselining.
- Java errors: confirm Maven runs on JDK 21 with `mvn -version`.
- Browser API/CORS errors: run both services, use the configured exact origin, and rebuild after changing public Next.js variables.
- Weather/provider errors: preserve unavailable/partial states and check provider entitlement and limits; do not add paid APIs without approval.
- Missing browser executable: install Chromium through Playwright before running its browser suite.
- Free preview slow to start: allow for free-host cold startup; this is not continuous production monitoring.
