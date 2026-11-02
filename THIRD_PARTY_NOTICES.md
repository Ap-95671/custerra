# Third-party components, data and assets

No application software licence has been selected. These notes do not grant rights to Custerra, its logo, third-party software or provider data. Preserve the applicable upstream notices and review the exact redistributed artifacts before release.

## Software

The project uses Java/Spring, PostgreSQL, React/Next.js, Leaflet, Python/FastAPI and scientific Python packages. Dependency manifests identify versions; their licences remain separate from any eventual application licence.

The npm lock inventory includes MIT, Apache-2.0, ISC, BSD, MPL-2.0, LGPL-3.0-or-later and compound declarations. Native image-processing distributions include LGPL-related terms. Review required notices, corresponding source/relinking obligations where applicable and artifact-specific distribution before publishing binaries or containers. Maven inherited licences, Python transitives and container OS components require additional review.

## Data and hosted services

| Source | Attribution / constraint |
| --- | --- |
| USGS earthquakes | Credit U.S. Geological Survey and retain event/source links; separately marked third-party material can carry other rights. [Policy](https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits) |
| NASA FIRMS / LANCE, VIIRS Suomi NPP | Preserve product/provenance/timestamps. General NASA data policy does not confer rights to marks or endorsement; product-specific review and live API acceptance remain open. [API](https://firms.modaps.eosdis.nasa.gov/api/area/) |
| GDACS | Retain GDACS and upstream provenance; downstream/commercial redistribution rights need review. [Terms](https://www.gdacs.org/About/termofuse.aspx) |
| Open-Meteo / GeoNames | Retain attribution. Weather data is CC BY 4.0; free hosted service access is non-commercial and has request limits. [Terms](https://open-meteo.com/en/terms), [geocoding](https://open-meteo.com/en/docs/geocoding-api) |
| OSRM / OpenStreetMap contributors / FOSSGIS routing | Retain route attribution and map-fix link; identify requests and respect service limits. [Routing service](https://routing.openstreetmap.de/about.html) |
| OpenStreetMap tiles | © OpenStreetMap contributors; retain visible attribution, normal caching and referrer behavior. No bulk/offline tile downloading. [Copyright](https://www.openstreetmap.org/copyright), [tile policy](https://operations.osmfoundation.org/policies/tiles/) |

Service access, data licensing and code licensing are distinct. A free API does not authorize unlimited commercial use or resale.

## Research dataset

Faroudja Abid (2019), *Algerian Forest Fires*, UCI Machine Learning Repository, DOI [10.24432/C5KW4N](https://doi.org/10.24432/C5KW4N), [dataset and CC BY 4.0 licence](https://archive.ics.uci.edu/dataset/547/algerian+forest+fires+dataset). The repository preserves the supplied CSV and records its hash. Preprocessing quarantines one malformed row without silently changing the source. Preserve attribution and describe any derived/transformed artifacts. See the [model card](docs/ml.md).

## Logo, screenshots and fonts

The Custerra logo was supplied by the owner. The owner explicitly confirmed creating and owning the logo. That statement is recorded provenance; no third-party endorsement is implied, and a future code licence does not automatically license the brand asset. Authentic application screenshots use public test locations and synthetic accounts, preserve source credit and do not constitute endorsement. Map/provider data retain their respective rights.

The interface uses system font stacks rather than bundled downloadable fonts. No rights to third-party logos or institutional endorsement are claimed.

Technology SVG icons: Devicon, pinned revision `32fe1d3b3f36ad11f90ab94f7db59738139c2bf6`; upstream MIT notice retained at [docs/assets/licenses/devicon-MIT.txt](docs/assets/licenses/devicon-MIT.txt). Icons were placed on neutral contrast tiles; trademarks remain their owners’ property. Banner and status artwork are custom local SVG.
