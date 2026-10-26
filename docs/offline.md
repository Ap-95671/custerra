# Offline journey copies — Milestone 11

On `/routes`, select a saved journey and choose **Save offline copy** while connected. Custerra fetches the saved journey and its latest page of notices, prepares the offline reader, then saves the copy in this browser. The confirmation is shown only after reader assets and data are stored successfully. Open **Offline copies** (`/offline.html`) to check the result before departure.

Copies include ordered waypoints, corridor/lookback/magnitude filters, the latest stored assessment and matching observations, source links, last check/attempt timestamps, saved check errors and up to the latest 50 informational notices. Saving does not run a new assessment: assessment freshness remains distinct from the copy's save time. A journey with no assessment remains explicitly unassessed.

## Offline behavior

After an explicit save has installed the reader, a disconnected navigation or reload within this site's scope opens the standalone reader. It needs neither Next.js runtime chunks nor the backend. Returning to online journeys uses the network again; the application does not substitute cached observations into live API responses.

Every copy is labeled read-only and potentially out of date, with save/expiry time, source server, last complete check and original assessment timestamp. UNKNOWN risk, truncated coverage and provider errors are retained. A saved ACTIVE status describes the server state when copied; it does not mean the browser is monitoring offline. The offline reader performs no mutations, background synchronization, queued checks, new notice generation or safety inferences. Milestone 17 web push is separate: it requires connectivity and does not refresh saved journey copies.

Map tiles, third-party assets, weather, historical snapshots and an emergency-contact directory are not cached. Existing generic guidance to follow local authorities remains visible. Source links need connectivity. No API response or page outside the dedicated offline reader is added to Cache Storage.

## Retention and removal

- Data is stored under `custerra.journey-copies.v1` in localStorage, scoped to this browser origin/profile.
- Copies are identified by source API URL plus journey UUID, preventing two backend installations with matching IDs from replacing each other's copies.
- At most five copies and one MiB of conservatively estimated UTF-16 storage are allowed. Saving another copy beyond the limit fails with an explanation; it does not silently evict another journey.
- Copies expire seven days after saving. Read-time validation removes expired, incompatible or malformed entries; the open reader also periodically checks expiry. The retention interval is an application policy, not a freshness guarantee.
- Use the reader's per-copy or clear-all controls to remove local data without changing server journeys. Successful deletion through this browser's journey UI also attempts to remove its matching offline copy, with an explicit message if local cleanup fails.
- Deletion on another device/server does not remotely erase a disconnected copy. Browser cleanup, private mode or storage eviction may remove copies earlier. Saved copies have no encryption at rest; anyone using the same browser profile can read saved route details. Online account authentication does not encrypt these explicit offline exports. Signing out now removes browser copies; session expiry alone preserves offline access.

## Implementation and deployment

`cacheManager.ts` validates API DTOs and registers `/journey-sw.js` during explicit saving. The notification center also registers this shared worker when the user explicitly enables browser push. Registration requires a secure context (HTTPS or localhost) and service-worker support. Storage/registration failures are visible to the user. The service worker precaches exactly four same-origin assets: `offline.html`, `offline-viewer.js`, `offline-store.js` and `offline.css`. Installation must finish before confirmation; a subsequent save repairs evicted reader assets. The worker intercepts those assets and failed same-origin GET navigations, excluding `/api/`. It does not intercept cross-origin requests or non-GET methods.

The portable shared storage module validates the reader's data shape, version, timestamps and bounds. The standalone reader builds DOM elements using textContent and accepts only credential-free HTTPS evidence links. Cached provider strings cannot become HTML. Service-worker updates remove only obsolete caches with the Custerra offline-shell prefix, leaving unrelated caches alone. Increment the shell cache version in both the worker and cache manager when changing the reader's asset contract; keep storage schema/version changes explicit. The worker script is served with `Cache-Control: no-cache`.

See [MDN's service-worker lifecycle documentation](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API/Using_Service_Workers) for deployment behavior. Offline availability is best-effort browser storage, not a guarantee of access during an emergency.

## Verification

Thirteen frontend unit/service tests and twelve browser tests pass, together with lint, typecheck and production build. Offline tests cover replacement and server separation, retention, malformed data, capacity/quota failures, text-safe rendering, disconnected navigation and reload, preserved incomplete/error states, local removal and reconnecting to the online app. Browser tests verify that only the four reader assets enter Cache Storage, no API mutation occurs, and the mobile reader has no horizontal overflow.

Milestone 11 originally left backend contracts unchanged. Subsequent milestones extend saved evidence while retaining offline compatibility.

Milestone 15 copies retain source coverage, provider checkpoints, normalized hazard provenance and revision notice types. The viewer displays these as saved evidence and does not refresh them. The shell cache is now version 3 (Milestone 17); existing version 1 data copies remain readable. The event bound is 1,700 across sources, still subject to the existing total 1 MiB storage limit. Oversized copies fail explicitly rather than dropping evidence.

Push identity deduplication uses a separate IndexedDB store containing notification IDs and receipt times, retained for up to seven days with a 1,000-entry bound. It stores no journey/evidence payloads and is not an offline notification inbox.
