# Milestone completion log

## 12 — Multi-hazard intelligence foundation (2026-10-09)

Implemented and fixture-verified: FIRMS VIIRS adapter, GDACS RSS adapter, normalized provenance, source-scoped identity/revision handling, source-isolated nearby queries, explicit coverage, provider/hazard filters, three map layers, source detail UI and additive Flyway V3. Existing risk, route and journey consumers remain USGS-only. No Git metadata was present; changes were reviewed against a temporary pre-change baseline.

Verification: Java 21 clean Maven verification passed **119 tests**, including **13 PostgreSQL migration/persistence/concurrency tests** and one optional downloaded-live-feed smoke test. Frontend: **15 unit tests**, lint, typecheck, production build and **14 browser tests** passed, including existing journeys and offline copies. Final backend verification used an identical source copy under `/tmp/custerra-m12-build` after duplicate generated class files appeared in the Desktop workspace and blocked class scanning. No source/package architecture was changed to work around that environment issue.

The downloaded live GDACS feed normalized 181 records; 12 drought records were explicitly reported unsupported. Live FIRMS verification remains **pending**, as requested by the user; no key is configured. The adapter is implemented and tested against fixtures, not claimed as verified live service. An additional running-API live smoke was not executed because automatic approval review hit a usage limit. These verification limitations carry forward rather than being reported as completed checks.

## 13 — Real routing and destination search

Implemented and verified: separate geocoding/routing providers, explicit place candidates, full polyline6 driving geometry, existing segment exposure evaluation, route map/evidence, provider attribution, bounded requests/cache, clear errors, and retained manual waypoint/journey flows.

Verification: **130 backend tests** (including database and GDACS smoke), **17 frontend unit tests**, lint, production build and **17 browser tests** passed. A live local API smoke resolved Delhi and Manali candidates, then retrieved the Himachal Pradesh route: approximately 528.5 km, 9,015 geometry segments, with a successful USGS assessment. This was a one-time smoke, not a travel recommendation. Generated driving routes are previews; saving/monitoring them remains a later extension. Free provider defaults require modest non-commercial usage and are documented in data-sources.md.

## 14 — Multi-hazard risk engine

Implemented and verified: preserved earthquake endpoint, new multi-hazard endpoint, source-scoped deterministic strategies, experimental thermal proximity, source failure isolation, complete provenance, explanatory wind context, explicit dominant indicators and unsupported UNKNOWN categories. Overall risk remains UNKNOWN; no average or scientifically unsupported all-hazard score is invented.

Verification: **137 backend tests**, **17 frontend unit tests**, lint, production build and **18 browser tests** passed. Weather is contextual only and cannot alter earthquake scores. This is an engineering indicator foundation, not validated wildfire risk/spread modeling.

## 15 — Multi-hazard journey monitoring

Implemented and verified: multi-source route monitoring, per-provider evidence baselines/checkpoints, significant event revisions, conservative disappearance semantics, transactional notice deduplication, legacy baseline migration, hazard/source detail UI and offline provenance retention. Failed/truncated sources cannot consume their monitoring checkpoint; usable partial feeds cannot imply disappearance.

Verification: **142 backend tests** (including PostgreSQL migrations/concurrency), **17 frontend unit tests**, lint, typecheck, production builds and **19 browser tests** passed. Full stored waypoint routes are monitored using segment indexes. Generated-route saving and real-time progress/GPS are not implemented.

## 16 — Authentication and ownership (2026-10-11)

Implemented and verified: Spring Security cookie sessions, BCrypt password hashing, CSRF, login/register/logout UI, explicit session expiry, per-account journey/history ownership, rate-limited authentication, and Flyway V5 preserving private unowned legacy data. Public route planning remains available. Logout removes browser offline copies; explicit offline exports remain readable after session expiry.

Verification: **151 backend tests**, including real HTTP session/CSRF cookies and PostgreSQL cross-user access tests; **20 frontend unit tests**, lint, typecheck, production builds and **21 browser tests** passed. Sessions and authentication throttles remain single-process; no password recovery, email verification or public deployment is claimed. See authentication.md.

## 17 — Notifications (completed 2026-10-25)

Implemented and verified: owned notification center/read state, eligibility/channel preferences, transactional queue, separate web-push dispatcher, attempt audit, bounded retries, ambiguous outcomes, concurrent claims, browser identity deduplication and evidence links. Logout/account switching revoke the session's subscription. Keys remain in ignored `.env.push` with mode 0600.

The initial fixture run passed 165 backend, 20 frontend unit and 24 browser tests. After the subsequent milestones, an opt-in live smoke used the verified operator GitHub profile URI as the VAPID subject and a separate temporary Chrome profile. The real push service accepted an explicitly labeled verification message, and Chrome's native notification API returned its expected title/body. No hazard record was fabricated. `LiveWebPushSmokeTests` passed separately; it is skipped unless explicitly enabled. The temporary subscription was revoked, browser profile/secret export removed and verification containers stopped. Dispatch remains opt-in for normal operation. This proves one live channel/browser path, not universal device reliability or a delivery SLA. See [notifications](notifications.md).

## 18 — Research foundation (2026-10-25)

Implemented, reproduced and tested: attributed/checksummed historical UCI dataset, deterministic preprocessing and rejected-row audit, chronological/geographic holdouts, frequency baseline, logistic regression, separate calibration/evaluation, error analysis, JSON model artifact and private Python inference API. Three substantive tests passed, including exported prediction equivalence and scope abstention. See [model card](ml.md).

This completes the scoped research foundation, not validation of an operational model. Results remain RESEARCH_ONLY; no live risk or notification consumes them. High false-positive counts and poor calibration are documented rather than hidden.

## 19 — Deployment and observability

Local deployment foundation implemented and verified: non-root multi-stage containers, same-origin web/API proxy, production configuration guard, graceful shutdown, minimal liveness/database readiness, protected Prometheus/provider status, provider/job/delivery instrumentation and bounded public request limits. Render Blueprint passed the published JSON schema; Compose configuration passed validation.

Verification: **170 backend tests passed**, one optional live GDACS check skipped; **24 frontend unit tests**, lint/typecheck/build and **24 browser tests** passed. Frontend/backend images built, and an isolated Compose PostgreSQL stack passed readiness, Flyway migrations, same-origin registration/login, cookie/CSRF handling and owned inbox access. See [deployment runbook](deployment.md).

Hosted release acceptance remains pending: Render is signed in and the approved free PostgreSQL instance is Available. The user selected a free demo; source repository access, app-specific database network access and hosted app verification remain outstanding. Local container success is not reported as production deployment, managed backup restore or monitoring integration.

## 20 — B2B API foundation (2026-10-25)

Implemented and verified: private developer organizations, owner/CSRF-protected credential management, high-entropy hashed keys with rotation/revocation/expiry, a separate stateless bearer namespace, atomic shared organization minute/day quotas, usage records and scoped USGS evidence/activity/route endpoints. Consumer resources and operations remain inaccessible through API keys. Markdown/API examples and validated OpenAPI 3.1 documentation are included. No billing or unreviewed commercial provider access was introduced.

Final regression verification passed **176 backend tests** (two opt-in live checks skipped: downloaded GDACS and web push), **24 frontend unit tests**, lint/typecheck, production container builds and **24 browser tests**. Six B2B integration tests cover secrecy, ownership, credential separation, scope, lifecycle, concurrency, minute rollover and plan authorization. The optional live push test passed separately when enabled. All three images built; a restarted isolated stack passed V7 migration/readiness, same-origin session/CSRF, notification inbox, API key issuance/usage/revocation/logout and separate research HTTP inference. Render Blueprint and OpenAPI schema validation passed. The verified backend sources matched the workspace.

## Remaining external acceptance

Milestone 19 is locally verified but not fully complete: Render is signed in, and the [deployment proposal](deployment-proposal.md) is ready for review. The user approved the private source repository and chose a free demo. Free PostgreSQL is Available; source publication/Render access, database network/TLS configuration and hosted app verification remain outstanding. The combined demo passed a 512 MB local rehearsal. Production backup/continuous-monitoring acceptance is outside the free-demo scope. No hosted deployment is claimed. Live FIRMS remains deferred by the user's explicit instruction. Milestones 17, 18 and 20 have their scoped implementations and verification; operational research-model release is deliberately not claimed.
