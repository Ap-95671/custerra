# Map frontend — Milestones 6–7

The existing Next.js-style scaffold is now runnable using Next.js 16.4, React 19.3, TypeScript and Leaflet 1.9.4. No project generator was run and no old files were deleted. npm was selected because the scaffold had no manifest or lockfile. `/`, `/map` and `/dashboard` render the explorer; unrelated empty app routes now explicitly return 404 rather than breaking compilation or pretending those features exist.

## User workflow

Choose coordinates through labeled fields, a map click, or a named preset. Set a 1–1,000 km radius and 1–30 day window, then explore. The selected query remains visible on the map. Map layers include earthquake observations, FIRMS thermal anomalies and GDACS advisories (see Milestone 12 below). Markers and cards expose magnitude when present, source evidence, event time, distance and unknown source severity. Selecting a card opens its map popup; hiding the layer retains the accessible event list.

The risk panel displays overall UNKNOWN separately from the experimental recent-earthquake activity indicator, confidence, explanation and expandable source factors. Null scores remain unknown, never zero. Coverage and truncation warnings remain explicit. It does not label observations as active alerts or travel-safety predictions. Other hazards remain unassessed.

## Components and data flow

LocationDashboard owns form/layer/selection state. DisasterMap owns Leaflet map and layer lifecycles, event popups and resize handling. DisasterList, RiskSummary and WeatherPanel render independent results. Leaflet is dynamically imported without server-side rendering; CSS is loaded from the installed package.

`useLocationAssessment` sends exactly one nearby request, one risk request and one weather request per submitted selection. The existing endpoints independently fetch USGS observations, so their snapshots can differ; each panel displays its own timestamp. This is an explicit current limitation rather than an implied shared snapshot. Combining both in a backend response or provider cache is future work. Rendering, toggling layers and opening details do not initiate requests.

A new selection aborts the previous requests and clears their data immediately. Late responses cannot overwrite the selected location. Nearby, risk and weather results settle independently, so a provider failure in one panel does not hide successful evidence in the other. Retry repeats all three requests for the current selection. Unmount cancels pending requests. The API service has a 35-second client timeout, status-specific failures and Zod runtime response validation. Malformed or failed responses never become successful empty states.

Provider titles render as React text or DOM textContent in popups; source links accept HTTPS URLs without credentials. Browser requests go directly to the configured API, with deliberate backend CORS. The UI contains no credentials or persisted location history.

## Map provider

Map defaults are centralized in `src/lib/maps/mapConfig.ts`. `NEXT_PUBLIC_MAP_TILE_URL` and `NEXT_PUBLIC_MAP_ATTRIBUTION` can select another provider at build time. Attribution is trusted deployment configuration, not user/provider-supplied content. Keep required visible attribution when changing providers.

Default tiles use OpenStreetMap with browser-managed caching and normal browser referrers. No tile prefetch, bulk download or offline cache is implemented. Review the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) when deploying; community tiles have no availability guarantee and are not an offline-map backend. Use a suitable provider for production scale or offline features. Tile failures produce a notice while coordinate entry, event lists and assessment continue to work.

Web Mercator excludes the poles. Coordinates above 85 degrees latitude remain queryable, but the map shows the edge of its extent with an explicit notice. Polar event markers are omitted from the basemap while their cards remain available. The map is an overview rather than an exact polar projection.

## Verification

Service tests cover query limits, safe evidence links, request construction/cancellation signal, malformed responses, provider errors and connectivity errors. Browser tests cover marker/card selection, layer toggling, unknown/numeric scores, loading, truncation, independent errors, stale-response cancellation, map clicks and mobile overflow. Browser fixtures and tile interception keep automated tests independent of external services.

A separate manual live check connected the real frontend, Spring Boot API, temporary PostgreSQL and USGS. Desktop and mobile screenshots were inspected. Milestone 6 required no backend changes; Milestone 7 adds the independent weather API and adapter.


## Weather context — Milestone 7

Weather requests send only coordinates; earthquake radius and lookback do not represent weather coverage. The panel displays modeled temperature, rain, total precipitation and wind with units and the provider aggregation interval, plus model grid coordinates and validity/retrieval times. Missing values display “Unavailable”; partial and stale responses have explicit notices. Weather errors preserve successful hazard and risk results, and retry is available. Weather is not used in risk scoring or labeled as a warning.

Seven service tests and eight browser tests now cover request routing, weather failures/retry, interval labeling, missing/stale conditions and delayed responses after a new selection. A live check with USGS and Open-Meteo also rendered the weather panel on desktop and mobile without browser errors or horizontal overflow.


## History and journeys — Milestones 8–10

Feature navigation links `/history` and `/routes`. HistoryExplorer creates/reloads immutable catalog snapshots and renders incomplete frequency explicitly. JourneyExplorer accepts ordered coordinates, assesses geometry, manages saved journeys and displays server scheduling configuration, check freshness/errors and paged informational notices. The validated JSON API client handles writes and 204 deletes without exposing provider bodies. These workflows are independent of the location explorer. Service and browser regression tests cover the combined UI. See [history and journeys](history-and-journeys.md).


## Offline reader — Milestone 11

The journey page can explicitly save essential data to this browser. Feature navigation links to a standalone reader that remains available on disconnected navigation/reload after preparation. Copies remain separate from online API results and preserve timestamps, errors, incomplete coverage and UNKNOWN risk. The service worker caches only its four reader assets, with no map tiles, API caching or queued mutations. See [offline behavior and storage policy](offline.md). See the milestone log for current verification totals.

## Milestone 12 layers and provenance

The map explorer now displays disjoint USGS earthquake, FIRMS thermal-anomaly, and GDACS global-advisory layers. Fixed symbols and labels supplement colors; GDACS earthquake/fire advisories remain in the GDACS layer. Layer toggles affect only map visibility, preserving the complete retrieved evidence list. At most 300 nearest enabled markers are rendered; a visible cap notice directs users to the evidence list. This bound prevents unbounded marker rendering without introducing a clustering dependency.

Popups contain family, information type, severity, confidence, distance, time and source evidence. Cards expand to show source identity, acquisition/retrieval/update timestamps, raw source attributes and measurements. Per-source coverage and retrieval failures are always visible, with retry for unavailable sources. All-source failure has a dedicated unavailable state and never uses the empty-success message. Thermal anomalies are expressly not confirmed wildfires. The location score remains the experimental earthquake indicator until Milestone 14. The existing offline journey reader is unchanged.

## Milestone 13 route planning

`/routes` now starts with independent From/To place search and explicit candidate selection, followed by a driving-mode route analysis form. Editing either place or any assessment filter cancels pending analysis, clears old results and requires a new selection where appropriate. Delayed search/analysis responses cannot overwrite newer input. The generated route map shows endpoints, the full path, nearby observations and highlighted matching segment indices. The details retain road distance/duration, freshness, source attribution, empty/incomplete evidence explanations, and independent hazard failure. Routing never claims a road is open or safe.

The old manual form remains under **Manual waypoint mode**, with its existing saved journey and offline workflows. Generated routes are previews and are not silently converted into saved manual journeys. All tests use synthetic provider/browser fixtures except separately enabled smoke checks.

## Milestone 14 risk panel

The location explorer requests `/risk/multi-hazard`. It displays the original earthquake indicator, a separately labeled experimental thermal-proximity index, explanatory wind context, highest available indicator categories, source coverage and explicit unsupported hazards. Overall UNKNOWN remains visually separate from individual indices. The old earthquake endpoint remains available for existing clients. Layer controls still affect evidence visibility only, never model outputs.

## Milestone 16 accounts

Registration and login use cookie sessions and fresh CSRF tokens. The global session provider guards saved journey/history views while route planning remains public. Expired sessions clear private views without redirects or automatic mutation retries; logout clears browser copies. See [authentication](authentication.md).

## Milestone 17 notification center

`/alerts` requires sign-in and displays owned evidence, read state and delivery attempts, with hazard/severity preferences. Browser subscription requires an explicit button and browser permission; configuration readiness is shown before opt-in. Push links load their specific owned item. The shared service worker handles push identity deduplication separately from offline journey storage. Fixture browser tests cover mobile layout, preference/read changes, evidence deep links and worker push events with native display stubbed. A separate opt-in smoke subsequently verified real push-provider acceptance and native Chrome notification receipt; headless fixtures still do not establish OS display. See [notifications](notifications.md).
