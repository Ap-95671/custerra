import type { Weather } from '../../types/weather';
import type { Result } from '../../hooks/useLocationAssessment';
import { safeSourceUrl } from '../../types/hazard';
import { formatTimestamp } from '../../lib/utils/dateUtils';
export default function WeatherPanel({ weather, hasQuery, onRetry }: { weather: Result<Weather>; hasQuery: boolean; onRetry: () => void }) {
  const data = weather.data;
  const value = (number: number | null, unit: string) => number === null ? 'Unavailable' : `${number.toFixed(1)} ${unit}`;
  return <section className="weather-panel" aria-label="Weather context">
    <div className="section-heading"><div><p className="eyebrow">ENVIRONMENTAL CONTEXT</p><h2>Weather at this location</h2></div><span className="tag">MODELED CONDITIONS</span></div>
    {!hasQuery && <p className="muted">Choose a location to load temperature, rainfall and wind context.</p>}
    {weather.loading && <p className="loading" role="status">Loading weather context…</p>}
    {weather.error && <div className="error" role="alert">{weather.error}<button onClick={onRetry}>Retry weather</button></div>}
    {data && <>
      {data.stale && <p className="notice">Weather data is more than two hours old. Do not treat it as current conditions.</p>}
      {data.partial && <p className="notice">Some weather measurements are unavailable; missing values are not zero.</p>}
      <div className="weather-metrics">
        <div><span>Temperature · 2 m</span><strong>{value(data.temperatureC, '°C')}</strong></div>
        <div><span>Rain · preceding {data.intervalSeconds / 60} min</span><strong>{value(data.rainMm, 'mm')}</strong></div>
        <div><span>Total precipitation · preceding {data.intervalSeconds / 60} min</span><strong>{value(data.precipitationMm, 'mm')}</strong></div>
        <div><span>Wind speed · 10 m</span><strong>{value(data.windSpeedKmh, 'km/h')}</strong></div>
      </div>
      <p className="timestamp">Valid {formatTimestamp(data.validAt)} · Retrieved {formatTimestamp(data.fetchedAt)} · Model grid: {data.gridLatitude.toFixed(3)}, {data.gridLongitude.toFixed(3)}</p>
      <p className="weather-note">Weather-model context, not a local station measurement or emergency warning. These values do not change earthquake scores or establish flood, landslide or travel risk.</p>
      <p className="weather-credit"><a href={safeSourceUrl(data.source.documentationUrl)} target="_blank" rel="noreferrer">Weather data by {data.source.attribution} ↗</a> · <a href={safeSourceUrl(data.source.licensingUrl)} target="_blank" rel="noreferrer">{data.license}</a></p>
    </>}
  </section>;
}
