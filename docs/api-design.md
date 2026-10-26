# API contract

Base path: `/api/v1`. Responses are JSON. Public hazard, risk, weather and planning reads need no authentication. Journeys, history snapshots and notifications require an authenticated owner. All unsafe methods require a CSRF token.

## GET /api/v1/health

HTTP 200:

```json
{"status":"UP","service":"Custerra API"}
```

This is liveness only, not a hazard assessment or readiness check.

## MVC errors

Example HTTP 404:

```json
{
  "timestamp": "2026-09-07T00:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Not Found",
  "path": "/api/v1/unknown",
  "validationErrors": []
}
```

Request-body validation failures return 400, `message: "Request validation failed"` and `validationErrors` entries with `field` and `message`. Malformed JSON, missing parameters, invalid parameter types and method-constraint violations return 400 without reflecting submitted values. Unsupported methods return 405 with the `Allow` header. Unexpected exceptions return 500 with a generic message.

This contract applies to exceptions processed by Spring MVC. Requests rejected earlier by the HTTP server or CORS processor may use that component's response format. Client code must check status and content type before parsing JSON.

## GET /api/v1/hazards

Returns stored events; this does not query a live provider or imply events are active.

| Parameter | Default | Validation |
| --- | --- | --- |
| `page` | `0` | Integer, 0–10,000 |
| `size` | `20` | Integer, 1–100 |
| `hazardType` | All types | Optional exact enum name |
| `severity` | All severities | Optional exact enum name |

Filters combine with AND. Order is `startedAt DESC, id DESC`. Invalid nonempty enum values, invalid integers and out-of-range pagination return 400. An omitted or empty optional filter is unfiltered. These are offset pages, not a consistent snapshot during concurrent ingestion.

```sh
curl 'http://localhost:8080/api/v1/hazards?hazardType=EARTHQUAKE&severity=HIGH&page=0&size=20'
```

HTTP 200 on a new database:

```json
{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

Each item has the fields below. `totalElements` and `totalPages` describe the filtered query. A page beyond the available results has an empty `items` list. Empty results mean no matching stored records, not an absence of hazards or proof of safety. Live nearby observations use a separate endpoint and are not stored by this read operation.

## GET /api/v1/hazards/{id}

Takes the internal UUID, not a provider event identifier. HTTP 200 returns one HazardEventResponse; malformed UUID returns 400 and missing UUID returns 404 using the common error contract.

| Field | Meaning |
| --- | --- |
| `id` | Internal UUID |
| `externalId`, `source` | Provider-scoped identity; unique pair |
| `hazardType` | EARTHQUAKE, FLOOD, LANDSLIDE, WILDFIRE, TSUNAMI, CYCLONE, HURRICANE, TYPHOON, TORNADO, VOLCANIC_ERUPTION, SEVERE_WEATHER |
| `title`, `description` | Source description; description may be null |
| `severity` | UNKNOWN, INFO, LOW, MODERATE, HIGH, CRITICAL; not location risk |
| `informationType` | OBSERVED_EVENT, ADVISORY, OFFICIAL_WARNING, MODELED_RISK, EXPERIMENTAL_PREDICTION |
| `latitude`, `longitude` | Point coordinates in degrees, [-90,90] and [-180,180] |
| `affectedRadiusKm` | Optional approximate extent in km; null means unknown |
| `startedAt`, `endedAt` | ISO-8601 timestamps; endedAt may be null and must not precede startedAt |
| `observedAt` | ISO-8601 timestamp when the record was observed by ingestion; distinct from event onset |
| `sourceUrl` | HTTP(S) source evidence link; clients must render external text safely |
| `confidence` | UNKNOWN, LOW, MEDIUM, HIGH; not a numerical probability |

Stored events are observations or source assessments, not Custerra emergency instructions. Null end time does not indicate that an event is still active. An observed earthquake must not be presented as an ongoing official warning.

There are no hazard write or active-warning APIs in this milestone. Test-only routes and synthetic fixtures are absent from the production JAR.


## GET /api/v1/hazards/nearby

Live, on-demand query through the selected USGS, FIRMS and GDACS adapters (Milestone 12). This route takes precedence over the stored UUID lookup. No database writes occur and no internal UUID is assigned: nearby results use `source` + `externalId`, not a stored `/hazards/{id}` link.

| Parameter | Default | Bounds |
| --- | --- | --- |
| `latitude` | Required | Finite degrees, -90 to 90 |
| `longitude` | Required | Finite degrees, -180 to 180 |
| `radiusKm` | 100 | Finite km, 1 to 1,000 |
| `days` | 7 | Integer lookback, 1 to 30 |
| `provider` | All connected sources | USGS, FIRMS, GDACS |
| `hazardType` | All supported types | HazardType enum; unsupported coverage stays UNKNOWN |

Invalid or missing required values return 400 using the common error structure. The server fixes the UTC interval once per request. The result contains:

- `query`: latitude, longitude, radiusKm, `from` and `to` (UTC timestamps).
- `fetchedAt`: completion timestamp for this query.
- `items`: objects containing `event` and `distanceKm`, sorted nearest first.
- `truncated`: true when any selected provider reaches its event bound; per-source limits and limitations are documented below. Truncated responses need not contain all nearest events.
- `sources`: provider ID, attribution, documentation URL, licensing URL and refresh policy.
- `coverage`: human-readable coverage limitation.

Each `event` is provider-neutral and contains externalId, hazardType, title, severity, informationType, latitude, longitude, affectedRadiusKm, startedAt, endedAt, source, sourceUrl, observedAt, sourceUpdatedAt, confidence, measurements, retrievedAt and attributes. USGS measurements include depthKm and magnitude when available. `observedAt` is event observation time; `retrievedAt` is retrieval time; `sourceUpdatedAt` is the source revision timestamp. For USGS, severity/confidence are UNKNOWN; affected radius/end time are null because the adapter does not establish those values. Missing magnitude is omitted from measurements.

HTTP 200 includes explicit per-source and aggregate coverage. An empty list may mean no matching retrieved events, unsupported coverage, or source unavailability: inspect coverageStatus and providerStatuses. It never establishes safety. Nearby provider errors are isolated in those statuses; the separate earthquake risk, route and history APIs retain 502/504 failures.

Nearby uses spherical surface distance (mean Earth radius 6371.0088 km), not depth-adjusted distance, travel distance or impact radius. The application rechecks distances and timestamps after normalization. Small discrepancies at the boundary can arise from provider distance conventions. USGS non-earthquake records are excluded by its mapper; other adapters supply their supported families. The API provides observations and advisories, not Custerra emergency orders or predictions.


## GET /api/v1/risk/location

Accepts the same latitude, longitude, radiusKm and days parameters and bounds as nearby. Retrieves one live observation snapshot and evaluates versioned, deterministic strategies. Provider errors remain 502/504; input errors remain 400. No writes or additional provider fetches are performed during scoring.

The response contains:

- `modelVersion`: `location-risk-v1-experimental`; `informationType`: `MODELED_RISK`.
- `query` and `assessedAt`: evidence scope and snapshot completion time.
- `overallRisk`, `score`, `confidence`: currently UNKNOWN, null, UNKNOWN, because all-hazard risk cannot be established.
- `explanation`: why overall risk is unavailable.
- `hazards`: eleven hazard entries. Each includes hazardType, scope, modelVersion, riskLevel, score, confidence, explanation, factors and limitations.
- `dataSources`: attribution and source metadata from the query.
- `sourceResultsTruncated`: whether the upstream result cap was reached.
- `limitations` and `safetyNotice`: missing evidence and safety constraints.

Earthquake scope is `RECENT_EARTHQUAKE_ACTIVITY`; its strategy version is `earthquake-activity-v1-experimental`. Its optional 0–100 score describes recent observed activity and is not a probability, forecast, official warning or travel-safety determination. Each factor carries its source event reference and numeric calculation inputs. Unsupported hazards have scope NOT_ASSESSED, null score/modelVersion, UNKNOWN risk/confidence and explicit unknown geographical relevance.

Empty/truncated evidence or unusable magnitudes withhold the earthquake aggregate score while retaining any available factors. Clients must render null as unknown, never coerce it to zero or a green safe state. LOW earthquake activity must be displayed with its scope and LOW confidence, alongside the overall UNKNOWN status. See [model rules and worked example](risk-model.md).


## GET /api/v1/weather/location

Required `latitude` (−90 to 90) and `longitude` (−180 to 180) must be finite. Radius and historical lookback are not weather inputs. Fetches current modeled conditions from Open-Meteo once, without persistence or risk scoring.

| Field | Meaning |
| --- | --- |
| `query` | Requested latitude and longitude |
| `gridLatitude`, `gridLongitude` | Provider model grid coordinates, which can differ from the selection |
| `informationType` | `MODELED_WEATHER` |
| `validAt`, `fetchedAt` | Model validity and API retrieval timestamps, ISO-8601 UTC |
| `intervalSeconds` | Provider aggregation interval; rain/precipitation apply to the preceding interval |
| `temperatureC` | Nullable temperature at 2 m, degrees Celsius |
| `rainMm`, `precipitationMm` | Nullable rain and total precipitation, millimeters |
| `windSpeedKmh` | Nullable wind speed at 10 m, kilometers/hour |
| `partial` | At least one of the four measurements is missing |
| `stale` | Validity is more than two hours before retrieval; an application freshness convention |
| `source`, `license`, `limitations` | Attribution, documentation/terms links, refresh description, CC BY 4.0 and interpretation limits |

Provider epoch seconds are converted to UTC instants. The adapter validates explicit units, grid coordinates, UTC offset and a 1–3,600 second interval. Invalid numbers, negative rain/precipitation/wind, or validity more than 15 minutes in the future fail normalization. Null measurements are preserved, never converted to zero. All-null measurements produce a partial response with unavailable values.

Invalid query inputs return 400. Upstream HTTP errors, malformed/oversized data or invalid units return 502; provider timeouts return 504 through the common error contract. Weather is model output, not a station observation, official warning, or flood/landslide prediction. Weather availability does not change the experimental earthquake indicator or overall UNKNOWN risk.


## Milestones 8–10 APIs

Historical snapshot creation/retrieval, route assessment and saved-journey lifecycle/check/notice APIs are documented with bodies, bounds and error semantics in [history-and-journeys.md](history-and-journeys.md). These introduce JSON POST/PATCH/DELETE operations. Stateless provider requests retain 502/504; a saved journey check records provider failures in `lastError` and returns the updated journey.

## Milestone 12: nearby multi-hazard evidence

`GET /api/v1/hazards/nearby` keeps latitude, longitude, radiusKm (1–1000) and days (1–30, default 7). It now queries USGS, FIRMS and GDACS by default, optionally filtered by `provider=USGS|FIRMS|GDACS` and/or a valid `hazardType` enum value. Unknown provider/type names return the centralized 400 error. Valid hazard types without a connected source return `coverageStatus=UNKNOWN` and no source claims.

Existing response fields remain. Added fields:

- `coverageStatus`: COMPLETE, PARTIAL, TRUNCATED, UNAVAILABLE or UNKNOWN for the **selected source query**, not real-world completeness.
- `providerStatuses[]`: provider, coverageStatus, retrievedAt (null on failure), limitations.
- Event `retrievedAt` and `attributes`: acquisition context and source metadata. USGS `observedAt` now correctly uses the event time; retrieval is explicit. Previously serialized records without these additive fields still deserialize.

Partial and even total provider failures return a structured HTTP 200 nearby response with explicit source status, rather than discarding successful evidence. This is a deliberate evolution of the former all-or-nothing nearby failure behavior. Do not infer success from HTTP status alone. All failures aggregate to UNAVAILABLE; a mixture of success and failure/incomplete sources is PARTIAL. A bounded source sets `truncated` independently, even when aggregate status is PARTIAL. Per-provider COMPLETE only means the selected query was returned without detected omission. The recent GDACS feed remains PARTIAL. Empty arrays, UNKNOWN and UNAVAILABLE never mean safety.

Identity is `(source, externalId)`; revisions prefer later `sourceUpdatedAt`, and providers remain distinct even at identical coordinates/times. FIRMS row identities are deterministic surrogates, not fire incident IDs. Nearby events remain on-demand and unpersisted. No global deduplication or incident grouping is claimed.

Examples:

```sh
curl 'http://localhost:8080/api/v1/hazards/nearby?latitude=32.24&longitude=77.19&radiusKm=100&days=7'
curl 'http://localhost:8080/api/v1/hazards/nearby?latitude=32.24&longitude=77.19&provider=FIRMS&hazardType=WILDFIRE&days=1'
curl 'http://localhost:8080/api/v1/hazards/nearby?latitude=32.24&longitude=77.19&provider=GDACS&hazardType=FLOOD'
```

Location scoring, supplied-route assessment, history and existing journey monitoring remain USGS-only through an explicit earthquake query path. USGS errors still produce 502/504 for those consumers; failed checks cannot silently advance their successful checkpoint. Multi-hazard risk and journey monitoring are separate later milestones.

## Milestone 13: places and generated routes

- `GET /api/v1/geocoding/search?q=Delhi`: 2–120 non-control characters, up to five `{id,name,region,country,location:{latitude,longitude}}` candidates plus source and retrievedAt. Empty results are valid. A short per-process provider throttle returns 429; repeated cached queries retain their original retrieval timestamp.
- `POST /api/v1/routes/plan`: body `{origin:{latitude,longitude},destination:{latitude,longitude},mode:"DRIVING",corridorKm:25,days:7,minMagnitude:4}`. Coordinate/filter limits match the supplied-route API. No implicit default travel mode; unsupported modes return 400. The response includes full `geometry` (polyline6), `geometryPrecision:6`, provider distanceKm/durationSeconds, bounds, segmentCount, source/retrievedAt, limitations, and a compact exposure assessment. Match segment indices refer to the decoded geometry. Full geometry is not persisted by this endpoint. Manual `POST /routes/assessment` remains unchanged.
- Route-not-found, provider distance over 900 km or snapping over 5 km returns 422. Rate limit returns 429. Provider transport/parse failures return 502/504. If route retrieval succeeds but USGS hazard retrieval fails, the route response is preserved with `assessment:null` and `assessmentError:HAZARD_PROVIDER_TIMEOUT|HAZARD_PROVIDER_UNAVAILABLE`. This must never be rendered as zero exposure.

The existing segment-distance evaluator consumes every decoded provider point (up to 20,000), rather than reducing a curved route to manual waypoints. Empty matches are phrased as absence in currently retrieved monitored earthquake evidence under the requested filters, never “safe”. Truncation and overall UNKNOWN remain explicit. Generated-route saving/monitoring is not yet connected; existing manual saved journeys continue to work.

## Milestone 14: multi-hazard assessment

`GET /api/v1/risk/multi-hazard` accepts the same coordinate/radius/days inputs as `/risk/location`. The older endpoint remains supported. The new response keeps familiar hazard results and adds `dominantHazards`, `assessedHazardCount` (scorable categories), `unassessedHazardCount`, per-provider coverage/status and `weatherStatus`. Hazard results add normalized `evidence` and `environmentalContext`; existing fields remain compatible. Provider failures are represented by coverage/UNKNOWN rather than converting them to LOW. Overall score remains null pending a calibrated aggregation. See risk-model.md for exact scope and thresholds.

### Multi-hazard journey monitoring (Milestone 15)

Journey checks now return `route-proximity-v2-multi-hazard` assessments with `coverageStatus` and `providerStatuses`. The journey adds `providerCheckpoints: [{ provider, lastSuccessfulCheck }]`; notice payloads add `stateVersion` and distinguish new, updated, returned and no-longer-matching evidence. Existing routes/assessment stays USGS-specific. Full semantics and migration compatibility are documented in [history and journeys](history-and-journeys.md).

## Milestone 16: authentication and ownership

See [session endpoints and ownership contract](authentication.md). Registration/login/logout/CSRF/current-user endpoints are under `/api/v1/auth`. Private resource requests return 401 without a session and 404 for another owner’s ID. API clients must retain session cookies and obtain CSRF tokens before writes; this is an intentional security requirement added to earlier write APIs.

## Milestone 17 notifications

`/api/v1/notifications` exposes the owned inbox, individual evidence, read state, preferences and browser subscription management. Every mutation requires CSRF. Delivery states distinguish push-service acceptance from uncertain outcomes; there is no device-delivery receipt. See the [notification API and setup](notifications.md#api) for endpoints and payloads.

## Milestone 20 external API

The separate bearer-only `/api/v1/b2b` namespace exposes USGS nearby evidence, the existing experimental earthquake indicator and supplied-route exposure. Account sessions manage clients under `/api/v1/developer`; those endpoints retain CSRF and owner checks. See [B2B authentication, quotas and examples](b2b-api.md) and [OpenAPI](b2b-openapi.yaml). Personal journey/history resources are not accessible through API keys.
