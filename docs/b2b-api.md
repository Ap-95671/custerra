# Custerra B2B API — v1

This foundation provides authenticated external access to the existing USGS evidence, experimental earthquake-activity indicator and supplied-route exposure calculation. It does not expose personal journeys/history, the retrospective research model, billing or unrestricted redistribution rights to every integrated provider.

## Credentials and ownership

An ordinary signed-in account can manage its own developer clients at `/api/v1/developer`. Each account receives one private developer organization when its first client is created. There are no team invitations, organizational identity verification or shared memberships yet. All management writes require the existing session cookie and a fresh `X-CSRF-TOKEN`; API keys cannot manage clients or read consumer resources.

| Method/path | Behavior |
| --- | --- |
| `POST /api/v1/developer/clients` | JSON `{"name":"Integration"}`; 201 with `{id,apiKey,expiresAt}` |
| `GET /api/v1/developer/clients` | Latest 100 owned client metadata records; no key or hash |
| `DELETE /api/v1/developer/clients/{id}` | Revoke; 204; foreign IDs return 404 |
| `POST /api/v1/developer/clients/{id}/rotate` | Atomically revoke old key and return a new credential |
| `GET /api/v1/developer/usage` | Organization ID, plan, limits and admitted requests today |

Keys are shown only at creation/rotation with `Cache-Control: no-store`. Each uses 32 cryptographically random bytes; only a SHA-256 hash and eight-character identification suffix are stored. Unlike passwords, these secrets have sufficient generated entropy for a fast hash. Keys expire after 90 days. At most five unexpired, unrevoked clients are active per account. Store credentials in a server-side secret manager; never embed them in a public browser bundle, URL or source repository.

## External authentication

Send `Authorization: Bearer <apiKey>` on `/api/v1/b2b/*`. This security chain is stateless: browser cookies cannot substitute for an API key, and API keys do not create consumer sessions. CSRF is disabled only for this bearer-only namespace (and separately authenticated operations endpoints). Consumer writes retain CSRF protection.

Example (an environment variable supplies the secret):

```sh
curl --fail-with-body -H "Authorization: Bearer $CUSTERRA_API_KEY" \
  'https://YOUR_CUSTERRA_HOST/api/v1/b2b/hazards/nearby?latitude=32.24&longitude=77.19&radiusKm=100&days=7'
```

## Mature services exposed

| Endpoint | Inputs | Result |
| --- | --- | --- |
| `GET /api/v1/b2b/hazards/nearby` | latitude −90…90, longitude −180…180, radiusKm 1…1000 (default 100), days 1…30 (default 7) | USGS observations, distances, source links, retrieval timestamps and query coverage |
| `GET /api/v1/b2b/risk/location` | Same coordinate/radius/day bounds | Experimental earthquake-activity indicator and evidence; overall risk remains UNKNOWN |
| `POST /api/v1/b2b/risk/route` | 2–20 ordered points, corridorKm 1…100, days 1…30, minMagnitude 0…10 | Epicenter-to-segment exposure with matching segment indexes and provenance |

Route body:

```json
{"points":[{"latitude":32.2,"longitude":77.1},{"latitude":32.4,"longitude":77.3}],"corridorKm":25,"days":7,"minMagnitude":4}
```

Additional geometric bounds from the consumer route evaluator still apply (including maximum total route length). The route is supplied geometry, not turn-by-turn navigation. See [history and journey calculation semantics](history-and-journeys.md).

All successes use an envelope with `apiVersion`, `calculationVersion`, `scope`, `coverageStatus`, `confidence`, `limitations` and `data`. Scope is `USGS_EARTHQUAKE_OBSERVATIONS_ONLY`; confidence remains UNKNOWN. `data` preserves the existing source response and calculation versions, rather than presenting an unexplained synthetic score. COMPLETE means the selected query was not reported incomplete; it is not a complete inventory of real hazards. A source failure returns an error, not an empty success. No externally exposed output establishes safe travel or predicts earthquakes.

## Quotas, usage and errors

| Plan | UTC daily requests per organization | Requests per UTC minute per organization |
| --- | ---: | ---: |
| DEVELOPMENT (default) | 100 | 20 |
| STANDARD | 10,000 | 120 |

All keys in an organization share these limits. PostgreSQL organization locks atomically reserve both windows before provider work; multiple keys/workers cannot spend the last slot twice. Authorized admitted requests count even when validation or a provider fails. Invalid/revoked/expired credentials and quota-rejected requests do not consume quota. Requests to unsupported B2B paths are admitted/charged before returning 404. Usage records store a fixed endpoint label, start time, status and duration; never coordinates, raw query strings or credentials. A crash or failed completion write can leave status/duration null; the reservation remains counted.

Successful admission returns `X-Request-ID` for correlation. Quota rejection returns 429 with `Retry-After` until the exhausted minute/day window resets. Limits are not billing. An operator can set an organization's plan via `PUT /api/internal/organizations/{organizationId}/plan` with JSON `{"plan":"STANDARD"}` and the separate operations token. Ordinary users and API clients cannot select their own plan.

Errors retain the common timestamp/status/error/message/path format. Typical statuses: 400 invalid input; 401 missing/invalid/expired key; 403 consumer CSRF/authorization failure; 404 unknown or foreign resource; 409 active-client limit; 429 quota exhausted; 502/504 provider failure/timeout; 503 authorization storage unavailable. Do not retry malformed requests; honor Retry-After and use bounded backoff for temporary failures. Rotation may require updating multiple application instances; an already admitted request may finish after revocation.

## Attribution and rollout limits

Retain USGS source credit, evidence URLs, information type, retrieval times, model versions, incomplete coverage and limitations in downstream displays. See [provider attribution/licensing](data-sources.md). FIRMS, GDACS, Open-Meteo and routing-provider content are deliberately excluded from this B2B surface until their applicable commercial terms and intended usage are reviewed. No warranty, emergency-service reliability, validated risk probability or production SLA is provided by this prototype.

Automated PostgreSQL tests cover key secrecy, ownership, credential boundaries, rotation/revocation/expiry, request validation, evidence response contracts, usage accounting and concurrent quota exhaustion. Hosting, load tests, backup recovery and a production API-client onboarding process remain deployment acceptance work.

A machine-readable [OpenAPI 3.1 contract](b2b-openapi.yaml) describes the external endpoints, authentication, limits, envelope and errors. Detailed existing evidence payloads remain documented in the consumer API contracts rather than being inaccurately simplified into a generic risk score.
