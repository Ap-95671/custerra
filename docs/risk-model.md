# Initial location-risk model — Milestone 5

`GET /api/v1/risk/location` returns an explainable, experimental assessment from one nearby-observation snapshot. It does not predict earthquakes or estimate a probability of injury, damage or disaster. The first available indicator describes **recent earthquake activity** only.

## Coverage and unknown results

Overall risk and overall score remain `UNKNOWN` and `null`: recent earthquakes alone cannot establish all-hazard location or travel risk. All eleven hazard categories are returned, but only earthquakes have a strategy. Unsupported hazards have unknown geographical relevance; the model does not assign artificial tsunami or volcanic risk to a location.

A complete, nonempty earthquake snapshot with usable magnitudes produces a scoped score and LOW confidence. An empty or truncated snapshot, missing/non-finite magnitude, or invalid evidence produces UNKNOWN and a null score. Available event contributions remain visible even when the aggregate score is withheld. A low activity indicator is not a declaration of safety.

## Versioned deterministic rules

Response model: `location-risk-v1-experimental`. Earthquake strategy: `earthquake-activity-v1-experimental`. These thresholds are initial engineering choices, not scientifically calibrated hazard boundaries. They must be validated before any safety-related use.

Each eligible observed earthquake contributes:

`round(magnitudePoints × distanceWeight × recencyWeight)`

The earthquake score is the **maximum**, not the sum, of these contributions. Repeating an observation does not inflate the score. Contributions are nonnegative integers on a 0–100 scale. The current nonempty rule set produces scores of at least 1; no observations produce null, never zero.

| Magnitude | Points |
| --- | --- |
| Below 4 | 20 |
| 4 to below 5 | 40 |
| 5 to below 6 | 60 |
| 6 to below 7 | 80 |
| 7 or above | 100 |

| Surface distance from query coordinate | Weight |
| --- | --- |
| Up to 25 km | 1.00 |
| Above 25 through 100 km | 0.75 |
| Above 100 through 250 km | 0.50 |
| Above 250 km within query radius | 0.25 |

| Age at query end time | Weight |
| --- | --- |
| Up to 24 hours | 1.00 |
| Above 24 through 72 hours | 0.75 |
| Above 72 hours through 7 days | 0.50 |
| Above 7 days within query window | 0.25 |

Levels describe this activity indicator: LOW for scores below 25, MODERATE for 25–49, HIGH for 50–100. For example, magnitude 5.2 at 60 km and 48 hours contributes `round(60 × 0.75 × 0.75) = 34`, or MODERATE activity with LOW confidence.

Only `OBSERVED_EVENT` earthquakes within the query's inclusive distance/time bounds qualify. Predictions, official warnings, other hazard types, future observations, and observations outside the window do not enter this calculation. Depth is not used: surface distance is not ground-motion intensity. Changing radius or lookback changes the evidence set, so scores across different query scopes are not directly comparable.

## Explainability and reproducibility

Every factor includes provider identity, source event ID and URL, event/revision timestamps, magnitude, surface distance, age, magnitude points, distance/recency weights and contribution. Factors are sorted by source and event ID for deterministic output. Age is measured relative to the fixed query end, not a fresh wall-clock read during scoring. The exact supplied snapshot and version determine the result; later live queries can differ because the window moves or the source revises events.

The model does not incorporate historical baseline, ground motion, terrain, weather, local vulnerability or exposure. Milestone 7 displays modeled weather separately as context; it is not an input to this strategy. Confidence is deliberately LOW for the scoped heuristic and UNKNOWN where the result is unavailable. These confidence labels are not probabilities. Source measurements remain evidence, not Custerra official warnings.

## Architecture and failure behavior

RiskController validates coordinates, radius and days and delegates to RiskService. RiskService fetches one nearby snapshot and passes it to RiskScoringService. Pure RiskScoringStrategy implementations assess one hazard; EarthquakeActivityStrategy is the first. Duplicate strategies for the same hazard fail startup rather than silently overriding one another. No database schema, external framework or background job is added.

Provider failures retain the existing 502/504 contract, without scoring partial failures or presenting successful empty assessments. No risk results are persisted. Future calibrated strategies and additional evidence sources can replace or extend this model without changing provider parsing or controller logic.

Follow local government and emergency instructions. Custerra is not an official emergency-warning authority.


Milestones 8–10 add separate historical observed frequency and route proximity evidence. Neither is silently folded into the existing earthquake activity score. Route overall risk stays UNKNOWN, and monitoring notices describe newly detected qualifying observations rather than a calibrated increase in danger. See [calculation and monitoring rules](history-and-journeys.md).

## Milestone 14 — Multi-hazard evidence strategies

The preserved `/risk/location` endpoint keeps the original earthquake response semantics. `/risk/multi-hazard` is the new frontend endpoint, version `overall-risk-v2-experimental`. It orchestrates the same strategy registry with source-specific snapshots. USGS evidence goes to the existing earthquake strategy; FIRMS evidence goes to `thermal-proximity-v1-experimental`. GDACS advisories remain evidence, not input to an invented flood/cyclone score. A failed unrelated provider does not erase a complete USGS indicator. Each strategy withholds classification when its required source query is incomplete, unavailable or empty.

**Thermal proximity is not wildfire probability, susceptibility, spread or confirmed fire danger.** It is an explicitly unvalidated detection-proximity index. Nominal/high VIIRS detection confidence is required. Base proximity points are 60 at ≤10 km, 40 at ≤25 km, and 20 otherwise inside the requested radius. Age multipliers are 1 at ≤24 hours, 0.5 at ≤72 hours, and 0.25 thereafter within the requested window. The index is the largest rounded single-event contribution; pixels and duplicate overpasses are not summed. Categories retain the existing display thresholds (<25 LOW, <50 MODERATE, otherwise HIGH), scoped solely to this index. Model confidence remains LOW. Low/unknown-confidence detections retain evidence and make the aggregate indicator UNKNOWN rather than silently disappearing. Empty, incomplete or truncated evidence also yields null score/UNKNOWN. Thresholds are engineering heuristics, not scientifically calibrated boundaries.

Weather contributes **explanatory environmental context only** in this milestone. Modeled wind is included for thermal observations when nonnegative and finite, within 50 km of the requested point, valid within the prior two hours and not flagged stale. It does not change a numerical score: fuel moisture, terrain and fire confirmation are missing, so a quantified spread adjustment would imply unsupported precision. Missing/stale/nonlocal weather is identified, not replaced by zero. Weather never changes earthquake scoring.

The aggregator never averages these differently scoped scores or treats unknowns as zero. Overall risk/score/confidence remain UNKNOWN/null/UNKNOWN because no calibrated all-hazard aggregation exists. `dominantHazards` identifies all ties at the highest available ordinal *indicator* category; it is not proof of the most dangerous real-world hazard. Counts distinguish scorable from unknown categories, source coverage stays separate, and all eleven hazard outputs remain visible. Flood, landslide, tsunami, cyclone and other unsupported models explicitly retain UNKNOWN geographical relevance. No fake coastal, terrain or volcanic relevance inference is made without data.

Responses retain normalized source evidence, calculation inputs, source confidence, observation/update/retrieval times, source metadata, version, limitations and any environmental factors. Reusing the same input snapshot yields deterministic outputs. Existing history, route and journey risk models are unaffected by this endpoint addition.
