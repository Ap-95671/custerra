# Notifications — Milestone 17

The notification center is implemented at `/alerts`, alongside existing per-journey informational notices. It preserves journey name, change type, hazard/information type, source evidence link, timestamp, distance/segment and explanation. Notifications describe retrieved evidence; they are not emergency orders, confirmed wildfire reports or validated risk changes.

## Eligibility and ownership

A separate `JourneyNoticeService` persists a new notice and invokes `NotificationService` inside the monitoring transaction. Source/event/type/state-version deduplication happens before eligibility. A failure rolls back both notice and notification; external network calls never occur in this transaction. Flyway V6 adds preferences, owned notification items, subscriptions, delivery state and attempts.

Account preferences control future notification creation, allowed hazard families, minimum **provider severity**, inclusion of UNKNOWN severity and push opt-in. Defaults enable in-app items for all families, include UNKNOWN and disable push. UNKNOWN is evaluated by its own checkbox; it is never converted into LOW. Preferences do not erase existing items or backfill old notices. Journey notices remain available even when notification eligibility suppresses an inbox item.

Notification list/detail/read/preferences/subscription APIs require the authenticated owner and CSRF on writes. Unknown or foreign notification IDs return 404. The list is paginated, 50 items per page. Read state is independent of outbound delivery. A notification click loads the specific owned item; expired sessions require sign-in.

## Web push setup

1. From the project root, run `node scripts/generate-vapid.mjs`. It creates `.env.push` with mode 0600, refuses to overwrite existing keys, and does not print secrets. `.env.*` files are ignored.
2. Set `VAPID_SUBJECT` in that file to a real contact URI (`mailto:...` or an HTTPS contact page). Keep the private key stable and secret; rotating it requires browser re-subscription.
3. Export `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`, and `NOTIFICATIONS_ENABLED=true` into the backend process, then restart it. Spring Boot does not automatically read `.env.push`. Keep the existing database and provider environment variables.
4. Sign in, open Notifications, choose **Enable this browser**, and grant browser permission. The UI displays server readiness and dispatcher status. It never requests permission automatically. HTTPS is required outside localhost; browser/OS support varies.
5. Create/activate a journey. Its first usable provider check establishes a baseline. A later eligible real evidence change creates the notice, inbox item and queued push. No invented hazard is created to demonstrate the feature.

A VAPID key pair is stored locally in ignored `.env.push` with mode 0600. The contact URI is the verified operator public GitHub profile, selected after the user delegated the choice. On 2026-10-25, a separate temporary Chrome profile subscribed through the actual notification UI. A real push-service request was accepted, and Chrome's native notification API returned the expected verification title/body. The message explicitly stated that it was a delivery test, not a hazard alert; no source observation or journey hazard was invented. This verifies one live channel/browser path. It does not prove human viewing, every OS/device or an emergency delivery SLA.

The temporary subscription was revoked and browser profile/secret export removed afterward. The normal scheduler default remains disabled until the operator opts in. Standard headless fixtures still stub the native display boundary while testing real service-worker events and IndexedDB deduplication; they remain distinct from this separate live smoke. `LiveWebPushSmokeTests` only runs when `CUSTERRA_WEBPUSH_SMOKE_FILE` points to a private JSON file containing an explicitly authorized test subscription (`endpoint`, `p256dh`, `auth`) and VAPID variables are exported. It sends a clearly labeled verification message and asserts push-service acceptance. Browser receipt must be checked separately; never use someone else's subscription for this test.

A maximum of five subscriptions is allowed per account. Subscriptions store the endpoint and encryption keys server-side and are never returned by the notification list API. Only HTTPS endpoints on the supported browser push hosts are accepted; credentials, ports, fragments, localhost and lookalike hosts are rejected. The JDK HTTP client follows no redirects and uses bounded connection/request timeouts. Unsupported browser push hosts fail explicitly rather than enabling arbitrary outbound URLs.

The adapter uses the WebPush Java SDK for AES128GCM payload encryption and VAPID, current Bouncy Castle, and a bounded JDK HTTP transport. It excludes the SDK's unused CLI/asynchronous HTTP libraries. No custom encryption or JWT signing implementation is introduced. The public key is exposed for browser subscription; private keys, endpoints and auth secrets are not logged.

Signing out revokes the browser subscription registered in that server session. A fresh sign-in also revokes the previous session's subscription before account switching. Explicit browser disable removes that endpoint and cancels pending deliveries. Other devices remain subscribed. Already accepted messages can still appear after sign-out; the opt-in UI explains that journey names/evidence may appear on a lock screen. Session expiry alone does not revoke durable push subscriptions.

## Dispatch and failure semantics

An opt-in scheduler checks every 30 seconds and claims at most ten pending deliveries per cycle. PostgreSQL row locks with `SKIP LOCKED` prevent two workers from claiming one pending delivery. Claims and attempt starts are committed before network I/O. Every attempt has start/completion timestamps and a sanitized result. One queue entry is unique per notification/subscription pair.

| State | Meaning |
| --- | --- |
| PENDING | Eligible and waiting, or an explicit temporary rejection is scheduled for retry |
| SENDING | A worker holds a 60-second delivery lease |
| SENT | The push service accepted the request; device receipt/display is **not** confirmed |
| FAILED | Non-retriable rejection, expired subscription, invalid request or retry limit reached |
| UNCERTAIN | Timeout, transport ambiguity or expired worker lease; accepted delivery cannot be ruled out |
| CANCELLED | Preference disabled, subscription removed or queued notice older than 24 hours |

HTTP 429/5xx rejections retry after one minute and then five minutes, at most three attempts. No automatic retry follows an ambiguous transport outcome or expired worker lease: this avoids knowingly re-sending a potentially accepted message. A crash after claim but before send may consequently lose an outbound message. In-app evidence remains available. 404/410 responses revoke the expired subscription while preserving delivery/attempt audit rows. Disabling preferences is rechecked before dispatch. A request already in flight cannot always be cancelled.

Push payload TTL is one hour. A stable push topic/browser tag and a bounded, seven-day IndexedDB identity store suppress repeated visible messages, including after the first notification was closed. Browser storage eviction can weaken this protection. This is best-effort delivery with explicit uncertainty, **not a claim of universal exactly-once delivery or emergency reliability**. DELIVERED is not reported because browser push services do not provide that receipt here.

## API

All paths below start with `/api/v1/notifications`:

| Method/path | Purpose |
| --- | --- |
| `GET ?page=0` | Owned notification list with read/delivery states |
| `GET /{id}` | One owned notification and source evidence |
| `POST /{id}/read` | Idempotently mark read |
| `GET /preferences`, `PATCH /preferences` | Retrieve/replace eligibility preferences |
| `GET /push` | Configuration readiness, dispatcher enablement, public VAPID key |
| `POST /subscriptions` | Save `{endpoint,p256dh,auth}` for the authenticated owner/browser session |
| `POST /subscriptions/remove` | Remove this owner's subscription by `{endpoint}` |

Email, SMS, quiet hours, digests and delivery receipts are not implemented. Notification retention policy and distributed operational metrics remain deployment work.

References: [WebPush Java SDK](https://github.com/web-push-libs/webpush-java), [browser subscription and explicit user gestures](https://developer.mozilla.org/en-US/docs/Web/API/PushManager/subscribe), [Bouncy Castle releases](https://www.bouncycastle.org/download/bouncy-castle-java/).

## Verification — 2026-10-25

The full backend run passed 165 tests with one optional downloaded-live-GDACS test skipped. Notification tests cover transactional rollback, preferences/UNKNOWN, ownership, recorded accepted attempts, transient retry limits, uncertain outcomes, expired subscriptions, worker lease recovery, concurrent dispatch and endpoint/key validation. The account suite verifies subscription revocation on logout and account switching. Frontend lint, typechecking, 20 unit tests, production build and all 24 browser tests passed. Browser fixtures verify mobile evidence/read/preferences, deep links and worker identity deduplication; native notification display is stubbed at the boundary described above. These fixture checks are separate from the successful live smoke described above.
