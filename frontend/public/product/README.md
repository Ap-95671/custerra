# Product capture provenance

`v3-explorer.jpg`, `v3-route.jpg`, and `v3-timeline.jpg` were captured on 4 November 2026 from the actual local V3 app and isolated backend. Locations are public San Francisco and Berlin/Potsdam. Sources: USGS, OSRM/OpenStreetMap contributors, NWS California, NASA EONET. Source requests are bounded and provider failures remain visible. OSM tiles were requested normally, without bulk prefetch or offline packs. These still images are not live feeds, hosted staging or production acceptance.

The older `location-explorer.jpg`, `driving-route.jpg`, and `offline-copy.jpg` are archived V2 staging captures from 1 November 2026. Landing captions identify them as archived; the offline capture uses a synthetic account. They do not demonstrate a live V3 monitoring session.

`v3-monitor.jpg` and `v3-history.jpg` were captured on 18 November 2026 in installed Chrome against the local V3 frontend, backend, and an isolated PostgreSQL instance. The monitor uses a disposable account, a real Berlin–Potsdam OSRM route, and a real manual check that returned partial coverage. The history preview shows a non-truncated USGS earthquake catalog query using public San Francisco coordinates. Both are historical walkthrough images.
