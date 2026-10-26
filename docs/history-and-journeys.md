# Historical exposure, route foundation and monitoring — Milestones 8–10

These features extend the location explorer with saved historical evidence and journey monitoring. The UI is available at `/history` and `/routes`, linked from the location explorer. History and stateless waypoint assessment remain earthquake-specific. Saved journey monitoring now retrieves USGS observations, FIRMS thermal anomalies and GDACS advisories (Milestone 15). Historical frequency and route proximity do not change the existing location-risk formula; overall route risk is UNKNOWN.

## Historical exposure

`POST /api/v1/history/earthquakes` retrieves and persists a new immutable snapshot. Example body:

```json
{"latitude":37.7749,"longitude":-122.4194,"radiusKm":100,"from":"2025-01-01T00:00:00Z","to":"2026-01-01T00:00:00Z","minMagnitude":4}
```

Coordinates must be finite and within latitude ±90 / longitude ±180; radius is 1–1,000 km and magnitude threshold 0–10. Supply explicit UTC instants spanning 1–3,660 days, starting in 1900 or later and ending no later than now. Missing fields and invalid input return 400. The response is 201 and includes a snapshot UUID. `GET /api/v1/history/earthquakes/{id}` reloads that exact stored result without provider access; missing IDs return 404. Keep the ID to reopen a result. There is no global snapshot-list endpoint yet.

The adapter uses the [USGS catalog API](https://earthquake.usgs.gov/fdsnws/event/1/), with explicit spatial/time/magnitude filters, newest-first order and a 2,001-record probe. It retains at most 2,000 normalized events and preserves truncation. The existing two-megabyte response cap and configured timeouts also apply; oversize responses fail with 502, not partial success. Provider errors/timeouts remain 502/504 and produce no stored snapshot. There is no pagination across a changing upstream catalog; reduce the window/radius or increase the magnitude threshold for truncated results.

The calculation `catalog-frequency-v1` uses the half-open interval `[from, to)`, rechecks distance and magnitude, and sorts evidence by event time/ID. The snapshot contains the query, normalized events with provenance and source revision timestamps, retrieval time, source credit, count, exposure years, annual frequency, explanation and model version. A threshold-filtered historical payload with missing/invalid magnitudes fails normalization.

```
exposureYears = durationInMilliseconds / (86,400,000 × 365.2425)
annualFrequency = matchingEventCount / exposureYears
```

For example, 10 records over 365 days produce about 10.007 events/year. A truncated count is a lower bound and its annual frequency is null. A complete response with zero matches returns zero observed frequency, which does not establish safety. Catalog completeness varies by geography, era and magnitude; a complete HTTP response is not proof of complete historical coverage. The threshold is user-selected, not an official definition of a “significant” earthquake. This is descriptive baseline exposure, not a recurrence probability, forecast or calibrated danger score.

Snapshots deliberately retain the evidence as retrieved: subsequent catalog corrections do not rewrite past calculations. A new POST creates a new snapshot. Snapshot evidence is stored separately from the existing hazard-event table, whose APIs retain their original behavior. Retention/cleanup policy and normalized cross-snapshot event indexing are future work.

## Route foundation

`POST /api/v1/routes/assessment` accepts:

```json
{"points":[{"latitude":37.7749,"longitude":-122.4194},{"latitude":37.85,"longitude":-122.25}],"corridorKm":25,"days":7,"minMagnitude":4}
```

Use 2–20 ordered, valid coordinate pairs, a 1–100 km corridor, 1–30 lookback days and magnitude threshold 0–10. Consecutive points form shortest great-circle segments on a sphere with radius 6,371.0088 km. Total length must be 0.001–900 km. Repeated vertices are supported within a nondegenerate route. Date-line crossings and polar segments are handled by spherical geometry.

The service queries one enclosing circle centered at the first point, with radius `totalLength + corridorKm` (at most 1,000 km). Triangle inequality ensures this encloses the supplied path and corridor. Each candidate is then filtered using its minimum distance to a segment, including endpoint clamping, and its magnitude. The response retains the closest zero-based segment index, distance, full observation evidence, retrieval time, query, length, source metadata, coverage and `route-proximity-v1` version. Results use stable distance/source/ID ordering.

The enclosing query inherits the 200-event recent-observation cap. A truncated enclosing response may omit matching route events, even if fewer than 200 matches remain after filtering; `truncated` stays true. Missing magnitude cannot meet a magnitude threshold. Epicenter proximity does not model shaking extent or whether an event occurs during travel.

This endpoint assesses supplied geometry. It does not obtain road directions, infer transport mode, calculate travel time, or recommend a safer route. A future routing-provider adapter can supply geometry without replacing assessment logic. No advanced path-finding algorithm or routing service subscription is introduced.

## Saved journeys and monitoring

| Method and path | Behavior |
| --- | --- |
| `POST /api/v1/journeys` | Body `{ "name": "Trip", "route": <route request> }`; saves PAUSED, returns 201 |
| `GET /api/v1/journeys?page=0` | Latest journeys, 50 per page, stable created-time/ID order |
| `GET /api/v1/journeys/{id}` | Saved geometry, status, last attempt, last complete check, last error and latest assessment |
| `PATCH /api/v1/journeys/{id}` | Body `{ "status": "ACTIVE" }` or `PAUSED` |
| `POST /api/v1/journeys/{id}/check` | Re-evaluates an ACTIVE journey; PAUSED returns 409 |
| `GET /api/v1/journeys/{id}/notices?page=0` | Saved informational notices, 50 per page, newest first |
| `DELETE /api/v1/journeys/{id}` | Returns 204; cascades seen-event identities and notices |
| `GET /api/v1/journeys/monitoring` | Reports server scheduler enablement and interval |

Names are nonblank and at most 120 characters. Page numbers range from 0 to 10,000. Missing journeys return 404. Route geometry and filters are immutable for each saved journey; create another journey to change them. Journey creation does not call a provider. The UI supports assessment, saving, activation, manual check, pause, deletion and paged notice review.

Monitoring uses `route-proximity-v2-multi-hazard`. The magnitude filter applies only to USGS earthquakes; GDACS advisories and FIRMS observations have their own source semantics. Matches retain closest segment indexes and full source evidence, allowing future remaining-segment selection without requiring GPS today. The current check covers the full immutable stored route. Reported points are compared with the corridor; affected-area polygons, travel-time forecasts and danger classifications are not calculated.

Each provider has an independent successful-evidence checkpoint. Its first COMPLETE or PARTIAL response establishes a baseline without notices. Later usable responses can create `NEW_MATCHING_OBSERVATION`, `UPDATED_MATCHING_EVIDENCE`, or `MATCHING_EVIDENCE_RETURNED`. Significant state includes hazard/information type, severity, coordinates/radius, confidence, event interval, measurements and reported bounding box. Retrieval timestamps, editorial titles and update timestamps alone do not generate notices. Older source revisions cannot reverse newer event state. Unchanged checks do not repeat notices.

Only COMPLETE source queries can create `NO_LONGER_MATCHING` for previously present evidence. This means the record no longer matches the query, possibly because it expired outside the lookback or the provider revised/removed it; it never means hazard resolution or safety. PARTIAL feeds establish positive evidence but cannot infer absence. UNAVAILABLE, UNKNOWN and TRUNCATED source responses do not advance that source's checkpoint or consume unseen evidence. Successful sources continue independently. The GDACS recent feed remains PARTIAL by design.

`providerCheckpoints` reports the last usable evidence check for each source. The assessment carries `coverageStatus` and detailed `providerStatuses`. `lastCheckedAt` retains its older meaning: last fully complete aggregate check. Incomplete aggregate checks set `lastError=INCOMPLETE_COVERAGE`; this may coexist with successful evidence and notices from individual sources. Exceptional top-level provider failures retain the previous assessment and use `PROVIDER_TIMEOUT`/`PROVIDER_UNAVAILABLE`. Check outcomes return HTTP 200; consumers must inspect coverage and error fields. Empty or missing evidence never establishes safety.

Checks, pause and deletion serialize using a database row lock. Event state is keyed by journey/source/event. Notices are unique by journey/source/event/type/state version, permitting later meaningful revisions while preventing concurrent duplicates. Checkpoints, evidence and notices commit atomically. Flyway V4 preserves old USGS baselines and existing notice identities; the first new check adopts legacy evidence without spurious revision notices. The scheduler rechecks due status after obtaining the lock. Provider I/O remains inside this transaction; worker leases and moving network I/O outside transactions are future scaling work.

Periodic checking is disabled by default. Set and export:

```sh
MONITORING_ENABLED=true
MONITORING_INTERVAL_MS=300000
```

Restart the backend after configuration changes. The interval must be 60,000–86,400,000 ms. A fixed-delay cycle checks up to 10 due ACTIVE journeys, oldest attempts first; startup waits one interval. Errors update attempt time so a failing journey does not immediately starve others. Manual checks work while scheduling is disabled. Milestone 17 adds a separate notification center and opt-in web-push queue; a separate real provider/browser delivery smoke passed. See [notifications](notifications.md) for configuration, eligibility and delivery semantics. Email, SMS and WebSocket delivery remain unimplemented. Reload the journey list and select a journey to view background updates.

Monitoring is best-effort, not an emergency-warning service. Long outages, fixed lookback windows, provider truncation, missing magnitudes and later catalog revisions can leave gaps. The system does not infer an increase in physical danger from a new observation. There is no automatic trip expiry, traveler progress tracking, historical backfill of missed monitoring windows or weather-derived warning logic.

## Storage and deployment

Flyway `V2__history_and_journeys.sql` adds immutable history snapshots, saved journeys, seen-event identities and informational notices. Versioned JSON evidence is stored as text, while identities, lifecycle state and scheduling timestamps are indexed relational columns. JDBC repositories use parameterized statements and explicit DTOs; existing JPA hazard persistence is unchanged. No dependency was added.

Milestone 16 adds account ownership. All journey and historical snapshot operations require a session and enforce ownership. Unowned legacy rows remain private and recoverable; see [authentication](authentication.md). The API now binds to `127.0.0.1` by default through `SERVER_ADDRESS`, and CORS permits JSON POST/PATCH/DELETE for the configured exact origins. Credentialed CORS and CSRF protection accompany server-side ownership checks; deployment hardening is still required before public use. No secrets are sent to the browser or embedded in snapshot evidence.

Automated coverage includes historical bounds/formula, transport parameters, segment interiors/endpoints, date-line/polar geometry, route filtering, invalid JSON/nested validation, PostgreSQL snapshot round-trip, baseline/deduplication, concurrent checks, incomplete/failing checks, due selection and cascade deletion. Frontend tests cover parsing, validated write requests, history reload, unknown/incomplete states and the journey lifecycle. Test observations remain fixtures only; production data comes from providers.
