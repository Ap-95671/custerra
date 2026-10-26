import type { HazardItem } from '../../types/hazard';
export const hazardLayers = {
  earthquakes: { label: 'Earthquake observations', symbol: '●', color: '#935435' },
  thermal: { label: 'Fire / Thermal Anomalies', symbol: '▲', color: '#a74314' },
  global: { label: 'Global Disaster Events', symbol: '◆', color: '#31599b' },
} as const;
export type HazardLayer = keyof typeof hazardLayers;
export function eventLayer(event: HazardItem['event']): HazardLayer {
  if (event.source === 'GDACS') return 'global';
  return event.hazardType === 'WILDFIRE' ? 'thermal' : 'earthquakes';
}
export function hazardLabel(event: HazardItem['event']): string {
  if (event.source === 'FIRMS') return 'Thermal anomaly · not a confirmed wildfire';
  return event.hazardType.replaceAll('_', ' ').toLowerCase();
}
export function informationLabel(value: string): string {
  return value === 'OBSERVED_EVENT' ? 'Observation' : value.replaceAll('_', ' ').toLowerCase();
}
