# Backend foundation decisions

The existing `com.nci.backend` package and `NaturalCalamitiesApplication` entry-point name are retained. Product-facing naming is Custerra. Empty feature files remain placeholders, not implemented modules.

Production controllers expose liveness and read-only stored hazards using record DTOs. HazardController validates query parameters and calls HazardService; HazardMapper owns entity-to-response mapping. Future controllers should validate request DTOs with Jakarta Validation and call services. Services should orchestrate domain rules and repositories; JPA entities should not become public API contracts.

`GlobalExceptionHandler` extends Spring MVC's standard exception handler, preserving HTTP status and protocol headers while returning one error DTO for MVC failures. It avoids returning exception messages, rejected values or stack traces. Unexpected errors log the exception type without credentials or potentially sensitive exception messages. Request-body validation exposes field names and constraint messages. Query validation uses MVC method validation; avoid class-level `@Validated` on future controllers unless deliberately handling its separate exception path.

CORS allows configured exact origins and supported read/write methods with credentials and the CSRF header. Spring Security sessions protect account-owned journeys, history snapshots and notifications. See [authentication](authentication.md) for ownership, session and migration behavior.

PostgreSQL is the runtime database. Flyway now owns schema changes, starting with `V1__initial_schema.sql`; JPA validates the resulting schema. Script-based initialization remains disabled. Automatic baselining and Flyway clean are disabled. The Spring Boot Flyway starter and PostgreSQL module follow [Spring Boot database initialization guidance](https://docs.spring.io/spring-boot/how-to/data-initialization.html). New schema changes require new versioned migrations.

The health response means that the API can serve a request. Database connectivity is established during application startup, but health does not continuously probe it. Add separate readiness checks when operational deployment requires them.

Hazard adapters translate provider-specific DTOs into a normalized model before domain services consume them. USGS is the first implementation. Risk strategies should carry evidence, uncertainty, source and model version. Route/journey models should follow working location assessment, without introducing queues, schedulers, ML or caches prematurely.


## Hazard domain — Milestone 2

`hazard/` contains the entity, enums, repository, mapper, service, DTOs and controller. The old empty `disaster/` and `ingestion/` files remain untouched; they are not competing implementations.

HazardEvent stores a source-scoped external identity, category, severity, information type, point coordinates, optional affected radius, event times, observation timestamp, confidence, source key and source URL. Provider keys are strings so new sources do not require enum changes. Provider-wide licensing and attribution metadata lives with each provider adapter.

Severity describes an event, not the queried location's risk. `UNKNOWN` severity/confidence preserve missing knowledge. Null radius means unknown extent, and null end time means no established end; neither implies zero impact or an active warning. The current model deliberately has no derived active flag. Records do not establish geographic relevance, current danger or future probability. The separate risk module now offers an experimental recent-earthquake activity indicator; stored events themselves carry no location-risk score.

Information type distinguishes observations, official warnings, modeled risk and experimental predictions. These are representable categories, not claims of implemented prediction capabilities. Future adapters must establish provenance and classification before persistence. Coordinates are degrees and radius is kilometers; the radius has a coarse global-distance upper bound of 20,040 km, a data-validation limit rather than a hazard-impact estimate.

Reads use transactions and database pagination, ordered by startedAt descending and UUID descending to break ties. Page size is 1–100 (default 20), page index 0–10,000. Optional category and severity filters combine with AND. Offset pages are stable for unchanged data; concurrent ingestion can shift page boundaries. Snapshot/cursor pagination can follow when that is a real requirement.

There are no public mutation endpoints or seeded events. Source/external-ID uniqueness prevents duplicate identities at the database level; provider refresh/upsert behavior remains planned with persistent ingestion. Bean validation and database checks enforce required classification/provenance, finite coordinate bounds and non-inverted event intervals. PostgreSQL integration tests use a separate explicit test database and transaction rollback.


## Provider and nearby query — Milestones 3–4

`HazardProvider` accepts a bounded `HazardQuery` and returns immutable provider-neutral observations. `UsgsEarthquakeProvider` owns HTTP and GeoJSON parsing; `UsgsMapper` maps evidence and validates source data. `NearbyHazardService` owns query windows, provider orchestration and distance/time filtering; its controller only validates parameters and delegates. `GeoDistance` handles spherical distances, including date-line and polar cases.

The existing persisted HazardEvent and HazardEventResponse retain their schema and UUID contract. Nearby observations use a separate normalized record because a fetched event has no database identity yet. This avoids misleading detail links or hidden writes during GET. Persistent ingestion can consume normalized observations later, with revision/deletion handling designed explicitly.

ProviderExceptions carry 502/504 status through the existing centralized error handler. Logs contain source/error category without coordinates, provider response bodies or exception messages. An empty body with HTTP 200 is invalid, while an explicit 204 is accepted. No schema changes or new dependencies were required. There is no scheduled provider refresh or retry loop. Milestone 12 adds the source-isolated partial-success nearby response described below.


## Initial assessment — Milestone 5

RiskService obtains a single NearbyHazardResponse snapshot. RiskScoringService dispatches by hazard type to pure RiskScoringStrategy components, returning unknown entries for unsupported hazards. EarthquakeActivityStrategy provides an explicitly unvalidated recent-event indicator. Overall risk is withheld because the current evidence cannot establish multi-hazard location risk or travel safety. Source parsing, distance filtering and query validation remain in their existing layers.

Scoring uses the supplied query end time and preserves source evidence, model version, inputs and contributions. Provider failures propagate through the existing error contract. No schema migration, new dependency or persistence write was introduced. Full rules and unknown-state behavior are documented in [risk-model.md](risk-model.md).


## Map frontend — Milestone 6

The preserved React/Next.js scaffold now hosts the map explorer. Frontend API access is centralized, validated at runtime and abortable; components render observations and experimental assessments without inventing missing risk values. See [frontend.md](frontend.md) for component boundaries, separate endpoint snapshots, tile-provider policy and test coverage. Backend contracts and schema are unchanged.


## Weather context — Milestone 7

WeatherController validates coordinates and delegates to WeatherService, which depends on WeatherProvider. OpenMeteoProvider owns bounded HTTP transport and configuration; OpenMeteoMapper validates and normalizes the provider payload into WeatherResponse. Clock injection makes timestamp checks deterministic. Provider failures use the existing centralized 502/504 error contract.

Weather uses a separate DTO and endpoint because modeled conditions are not stored hazard events. Frontend weather state settles independently of nearby observations and risk assessment. No risk formula, database migration, persistence write, dependency or scheduled job was added. See [data sources](data-sources.md) for attribution, access terms and freshness limitations.


## Milestones 8–10

HistoricalProvider extends catalog retrieval without relaxing recent-query bounds. HistoryService stores immutable evidence/calculation snapshots. RouteGeometry and RouteRiskService assess supplied segments independently of persistence. TripMonitoringService orchestrates journey state, complete-check baselines and deduplicated notices with database transactions; the opt-in scheduler uses the same service. New JDBC repositories share the existing datasource and Flyway lifecycle. Details and limitations: [history and journeys](history-and-journeys.md).


## Offline support — Milestone 11

An explicit save action validates journey/notice DTOs, prepares a minimal service-worker reader and persists bounded, versioned browser copies. A shared storage module enforces expiry/capacity and supports both the React save workflow and standalone reader. Offline evidence is isolated from live requests and cannot trigger server mutations. Backend architecture, persistence and scoring remain unchanged. See [offline.md](offline.md).

## Milestone 12 extension

`provider/firms` and `provider/gdacs` implement the existing `HazardProvider` interface alongside `provider/usgs`. The meaningful shared transport helper bounds payloads/timeouts and sanitizes upstream exceptions. Each source retains its own parsing/mapping logic. `NormalizedHazardEvent` adds retrieval time and immutable source attributes, retaining a compatibility constructor and legacy JSON support. The information-type enum adds ADVISORY, reflected by additive Flyway V3. Existing V1/V2 migrations are unchanged.

`NearbyHazardService` selects sources by provider/capability, isolates each failure, reduces same-source identities to their latest revision, then applies geospatial/time/type filters and deterministic ordering. The DTO exposes source-specific coverage alongside aggregate status. `earthquakes()` is an explicit compatibility path for the existing activity/route/monitoring implementations; it requests only USGS and preserves their failure behavior. No persistence ingestion, risk strategy, incident correlation, or monitoring scope expansion is implied by the new map layers.

## Milestone 13 extension

`route/provider/GeocodingProvider` and `RouteProvider` isolate city search and routing contracts from UI and risk logic. Their Open-Meteo and OSRM adapters perform bounded transport, source payload validation and normalization. `RoutePlanningService` owns the bounded geocoding cache, per-process request pacing, route-to-exposure orchestration and partial failure handling. `RouteRiskService.assessGenerated` reuses the existing geometry evaluator with an internal vertex bound while the manual endpoint retains its original validation. Full polyline6 travels across the new API boundary; decoded points are transient. No database migration or dependency was required. Existing journey geometry/storage remains unchanged.

## Milestone 16 extension

Spring Security handles form login, session fixation protection, logout, password hashing and CSRF. Account-owned repository queries isolate journeys and history; the internal scheduler continues across owned active journeys. Flyway V5 preserves and pauses unowned legacy data. Existing public hazard/provider APIs remain available. Sessions and authentication throttles are per-process until deployment work introduces shared infrastructure.

## Milestone 17 extension

`JourneyNoticeService` persists a deduplicated monitoring notice and invokes notification eligibility in the same transaction. `NotificationService` creates an owned inbox item and, when opted in, per-subscription delivery rows. The separate dispatcher claims rows using PostgreSQL `SKIP LOCKED`, records attempts before network I/O, and sends through `PushGateway`. Web push uses SDK encryption/VAPID with a bounded JDK HTTP client. Explicit transient rejections retry; ambiguous transport or expired leases remain UNCERTAIN. Flyway V6 adds the queue, preferences, subscriptions and audit records. No external call occurs inside the monitoring transaction. See [notification setup, lifecycle and verification limits](notifications.md).

## Milestones 18–20

Research lives in the separate Python service with a historical scope and JSON artifact; it is not called by live risk services. Deployment adds a same-origin web/API boundary, private operational endpoints, provider/job telemetry and bounded public request limits. B2B uses its own stateless security chain and reuses USGS evidence/calculation services without inheriting consumer account permissions. Flyway V7 adds developer organizations, hashed API credentials, atomic organization quota buckets and per-admitted-request usage. See [research](ml.md), [deployment](deployment.md) and [external API](b2b-api.md).
