import { describe, expect, it } from 'vitest';
import { nearbySchema } from '../src/types/hazard';
import { eventLayer, hazardLabel } from '../src/lib/maps/hazardLayers';
const timestamp = '2026-10-09T12:00:00Z';
const event = { externalId: 'fixture', source: 'FIRMS', title: 'Thermal anomaly', hazardType: 'WILDFIRE', informationType: 'OBSERVED_EVENT', latitude: 0, longitude: 0, sourceUrl: 'https://firms.modaps.eosdis.nasa.gov/', startedAt: timestamp, severity: 'UNKNOWN', measurements: { frp: 10 }, observedAt: timestamp, retrievedAt: timestamp, sourceUpdatedAt: null, confidence: 'MEDIUM', attributes: { confidence: 'n' } };
describe('multi-hazard evidence contract', () => {
  it('retains provenance and per-provider unavailability', () => {
    const parsed = nearbySchema.parse({ fetchedAt: timestamp, truncated: false, coverage: 'incomplete', sources: [], coverageStatus: 'PARTIAL', providerStatuses: [{ provider: 'GDACS', coverageStatus: 'UNAVAILABLE', retrievedAt: null, limitations: ['PROVIDER_TIMEOUT'] }], items: [{ event, distanceKm: 0 }] });
    expect(parsed.items[0].event.attributes?.confidence).toBe('n');
    expect(parsed.items[0].event.retrievedAt).toBe(timestamp);
    expect(parsed.providerStatuses?.[0].coverageStatus).toBe('UNAVAILABLE');
  });
  it('assigns disjoint layers, including GDACS earthquake and wildfire advisories', () => {
    expect(eventLayer(event)).toBe('thermal');
    expect(eventLayer({ ...event, source: 'USGS', hazardType: 'EARTHQUAKE' })).toBe('earthquakes');
    expect(eventLayer({ ...event, source: 'GDACS' })).toBe('global');
    expect(eventLayer({ ...event, source: 'GDACS', hazardType: 'EARTHQUAKE' })).toBe('global');
    expect(hazardLabel(event)).toContain('not a confirmed wildfire');
  });
});
