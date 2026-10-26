# Milestone 0 — repository audit

Audit date: 2026-09-07. Scope of this implementation: Milestones 0 and 1.

## Initial findings

- All application, configuration, documentation, SQL, infrastructure and test files were zero bytes. Only Finder/IDE metadata contained content. No working behavior existed to preserve.
- Backend package root: `com.nci.backend`, with feature placeholders for alert, auth, disaster, ingestion, monitoring, risk, route and user; shared configuration, security, exception, utility and WebSocket placeholders.
- No `pom.xml`, Maven wrapper or dependency declarations existed. Application entry point, configuration and test files were empty, so an initial Maven build was unavailable.
- No implemented entities, repositories, services, controllers, endpoints or database configuration existed. SQL and migration files were empty.
- Frontend had `src/app` pages/layout and feature components, hooks, types, API, maps, store and offline placeholders. Naming suggests React/Next.js App Router, but no `package.json`, lockfile, configuration or source implementation existed. Framework version, package manager and build status cannot be established from empty files.
- ML, database, docs and infrastructure directories were inspected; all were placeholders.
- No applicable `AGENTS.md` or Sites hosting configuration was found.
- `git status` failed because the workspace has no `.git` directory. No repository was initialized and no commits were made. A pre-edit file-hash inventory was used to verify preservation instead.

## Implementation decisions

1. Preserve the package, feature directories and all placeholder files.
2. Add only dependencies required by the backend foundation: Web MVC, validation, JPA, PostgreSQL and MVC testing, using Java 21 and Spring Boot 4.0.7.
3. Implement public API liveness, DTOs, centralized MVC errors and explicit CORS.
4. Require database credentials through the environment; disable automatic schema creation and SQL scripts.
5. Verify error and validation behavior without live services, then smoke-test real PostgreSQL startup separately.
6. Document the current limitations. Defer frontend authoring, hazard entities, provider clients, security, migrations and future services to their milestones.

## Verification

- Maven `verify`: 10 MVC tests passed; executable JAR packaged on Java 21.
- Packaged JAR smoke test against an isolated local PostgreSQL database: successful startup and health 200, consistent JSON 404 response, and confirmation that test-only routes are absent. The API and temporary database were stopped afterward.
- File-hash comparison: no original files deleted; frontend, ML, database and infrastructure scaffolds unchanged. Only foundation implementation and documentation files were modified or added.
- Frontend checks: unavailable because no package manifest or runnable frontend exists.
- Git diff/status: unavailable because this directory is not a Git repository.

## Milestone 2 follow-up

Implemented the hazard domain, repository, mapper, read service and versioned controller while retaining all previous scaffold files. Flyway now runs the first meaningful schema migration; Hibernate continues to validate the schema. GET list and UUID lookup endpoints expose DTOs, with bounded pagination and optional type/severity filters. There are no live providers, public writes, production fixtures, active-event classifications or risk scores yet.

Verification on 2026-09-07: Maven verify passed all 32 tests, including six opt-in tests against isolated PostgreSQL. Checks covered migration application and repeat execution, entity round-trip, duplicate source identity, database coordinate constraints, filtering, pagination, DTO mapping, validation, HTTP errors and existing health/CORS behavior. The packaged API started against the migrated database, returned an empty hazard page after fixture rollback, and preserved JSON 400/404 responses. The temporary API and database processes were stopped afterward.

No original files were deleted. Frontend, ML and infrastructure application files remain unchanged. Git is still unavailable; a file-hash inventory was used for scope review. Finder metadata changes were left untouched.

## Milestones 3–4 follow-up

Implemented on 2026-09-08: a provider abstraction, USGS GeoJSON transport/mapper, immutable normalized observations, centralized source metadata and a live nearby endpoint. HTTP requests are bounded by radius, lookback, result count, response bytes and connect/read timeouts. Nearby results include query scope, retrieval time, source revision times, distances, attribution and truncation. Malformed/provider failures use 502/504 instead of successful empty results.

Verification: Maven verify passed all 45 tests with zero failures or skips, including the six isolated PostgreSQL tests. Mocked provider tests cover the outgoing query, coordinate order, epoch timestamps, unknown severity, non-earthquake filtering, absent magnitude, deduplication/truncation, malformed and oversized responses, upstream errors and timeouts. Nearby tests cover validation, routing, distance/time filtering, date-line/polar/antipodal distances and failure propagation. Existing persisted APIs and schema tests still pass.

A packaged-application smoke test on temporary PostgreSQL queried live USGS data around San Francisco (500 km, seven days). It returned 200 observations with truncated=true at 2026-09-08T18:21:25Z. No live data was inserted into the database; both temporary processes were stopped. No new dependencies or schema migrations were required, and no original files were deleted. Frontend/ML/infrastructure scaffolds remain unchanged. Git remains uninitialized, so scope was checked using the pre-edit file-hash inventory.

Persistent provider ingestion/revisions/deletions, scheduled refresh, caching, additional hazards, location risk scoring and map UI remain future work. The live nearby endpoint intentionally does not claim to provide all-hazard coverage, active warnings or a validated risk estimate.

## Milestone 5 completion

Completed verification on 2026-10-08 after resuming the implementation. Added the versioned location-risk endpoint, immutable assessment/factor DTOs, pure strategy interface, earthquake activity strategy and hazard strategy dispatcher. Overall and unsupported-hazard risks remain UNKNOWN; empty/truncated/invalid evidence withholds the earthquake score. Existing provider and persistence contracts are unchanged. No dependencies, schema migrations, persistence writes, frontend edits or scheduled work were added.

Maven verify passed all 64 tests with no failures or skips, including six PostgreSQL regression tests and 19 new risk tests. Risk tests cover exact magnitude/distance/recency boundaries, rounding, level cutoffs, source evidence, deterministic ordering, strongest-event aggregation, unknown states, excluded observations, strategy registration, query validation, single-fetch orchestration and 502/504 propagation.

The packaged API passed health, stored-hazard and invalid-coordinate checks against a temporary PostgreSQL database. A live USGS-backed request near San Francisco (100 km, seven days) returned 16 factors and an experimental earthquake activity score of 11/LOW, while overall risk stayed UNKNOWN. This is a transient verification result, not a safety recommendation or a seeded product example. Temporary application/database processes were stopped afterward. The root README, architecture, API contract and risk-model documentation describe scope, exact rules and limitations. Git remains uninitialized; no commits were created.

## Milestone 6 completion

Completed on 2026-10-08. Made the existing Next.js-style frontend runnable with React, TypeScript, Leaflet, npm configuration and a lockfile. Added coordinate and map selection, named presets, observed-event markers/popups, a layer toggle, source cards, experimental risk context, evidence expansion and independent loading/error/empty states. API access is centralized, abortable and runtime-validated. No backend files or schema changed, and no original files were deleted. Unimplemented route placeholders explicitly return 404; map/dashboard aliases render the explorer.

Verification: frontend lint, TypeScript checks and production build passed. Six API/service tests and six browser tests passed. Browser tests intercept hazard APIs and map tiles and cover loading, numeric/null scores, truncation, mobile overflow, map selection, marker detail, layer visibility, partial failure and stale-response cancellation. The final npm audit reports zero vulnerabilities, including development dependencies.

A separate live browser check connected the real frontend, Spring Boot API, temporary PostgreSQL and USGS: 16 earthquake observations and the risk panel rendered without browser errors. Desktop (1440 px) and mobile (390 px) screenshots were inspected, with no horizontal overflow. Temporary servers were stopped after verification. Documentation covers setup, CORS, build-time public configuration, separate nearby/risk snapshots, OSM attribution/policy and polar basemap limitations. Next planned milestone is weather/environmental context; name geocoding, routes and offline support remain unimplemented.


## Milestone 7 completion

Completed on 2026-10-09. Added a provider-neutral weather interface, Open-Meteo transport and mapper, validated configuration, immutable DTO and coordinate-based weather endpoint. Added an independent frontend weather panel with temperature, rain, total precipitation, wind, provider interval, model grid coordinates, source credit, timestamps, missing values and freshness notices. Weather is explicitly modeled context and does not alter risk scoring.

Verification: all 73 backend tests passed with zero failures or skips, including six isolated PostgreSQL tests and nine new weather tests. New tests cover units and epoch conversion, query construction, missing/stale data, invalid payloads, response limits, upstream errors/timeouts, coordinate validation and single-fetch service orchestration. Frontend lint, typecheck and production build passed, along with seven service tests and eight browser tests. Browser coverage includes weather failures/retry and protection against delayed previous-selection responses.

A live browser check connected the frontend, packaged backend, temporary PostgreSQL, USGS and Open-Meteo. Earthquake observations, experimental risk and real modeled weather rendered on desktop and mobile without browser errors or horizontal overflow. Screenshots were inspected and temporary services stopped. No dependencies, migrations or risk-model changes were introduced. Original files were preserved. Git remains uninitialized; no commits were created. Next planned milestone is historical analysis.


## Milestones 8–10 completion

Completed on 2026-10-09. Added immutable historical earthquake snapshots with explicit windows, magnitude thresholds, source evidence, model version and observed annual frequency. Added supplied-waypoint route geometry, spherical segment proximity, bounded enclosing catalog queries and UNKNOWN route risk. Added persisted PAUSED/ACTIVE journeys, complete-check baselines, deduplicated informational notices, manual checks and an opt-in bounded scheduler. Frontend navigation exposes the historical and journey workflows, including actual server scheduler status.

Flyway V2 adds snapshot, journey, seen-identity and notice tables. New JDBC repositories share the existing datasource; existing hazard persistence and location-risk calculations remain intact. No new dependencies, synthetic production events, routing subscriptions or outbound notification services were added. Saved journeys are local installation data without account ownership, so the backend now binds to loopback by default. Exact-origin CORS supports the new JSON mutations.

Final Maven verification passed 92 tests with zero failures or skips, including 12 PostgreSQL tests. New coverage includes reproducible historical counts, time/magnitude boundaries, required/null JSON inputs, provider query limits, segment interiors/endpoints, date-line/polar geometry, route filtering, snapshot persistence, baseline and notice lifecycle, provider failures, incomplete coverage, cascade deletion, concurrent deduplication and scheduler isolation/bounds. Frontend lint, typecheck, production build, ten unit tests and ten browser tests passed.

A live USGS check saved and reloaded historical evidence and completed route assessment, journey creation, activation, a complete baseline check, pause and deletion. Desktop/mobile screenshots were inspected with no browser errors or horizontal overflow. All temporary application/database services were stopped. Duplicate generated class files encountered during packaging were removed through Maven clean; no source files were deleted. Git remains uninitialized and no commits were created. Detailed API semantics, deployment configuration, provider limitations and future scope are documented in history-and-journeys.md.


## Milestone 11 completion

Completed on 2026-10-09. Added opt-in browser copies of saved journeys, assessment evidence and up to fifty recent notices. A standalone service-worker reader opens on disconnected navigation and reload; copies retain source/check timestamps, UNKNOWN risk, errors and incomplete coverage. Added bounded/versioned storage, seven-day expiry, local deletion and privacy/availability explanations. Map tiles and API responses are not cached; no mutations or monitoring work are queued offline. No dependency, backend change or database migration was introduced.

Frontend lint, typecheck, production build, thirteen unit/service tests and twelve browser tests passed. Browser verification exercised an actual installed service worker with network disconnection, reload, reconnect, cache inspection, local removal, expiry and safe text rendering. The mobile screenshot was inspected and no horizontal overflow occurred. All test servers stopped after verification. The supplied master prompt has no Milestone 12; its scope was requested from the user and remains pending.
