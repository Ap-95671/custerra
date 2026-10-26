import { safeSourceUrl, type Nearby } from '../../types/hazard';
import type { Result } from '../../hooks/useLocationAssessment';
import { formatTimestamp as time } from '../../lib/utils/dateUtils';
import { hazardLayers, eventLayer, hazardLabel, informationLabel } from '../../lib/maps/hazardLayers';
export default function DisasterList({ hazards, hasQuery, selected, onSelect, onRetry }: {
  hazards: Result<Nearby>; hasQuery: boolean; selected: string | null; onSelect: (id: string) => void; onRetry: () => void;
}) {
  const items = hazards.data?.items ?? [];
  const unavailable = hazards.data?.coverageStatus === 'UNAVAILABLE';
  return <section className="events-section"><div className="section-heading"><div><p className="eyebrow">03 — SOURCE EVIDENCE</p><h2>Nearby hazards {hazards.data && <span className="count">{items.length}</span>}</h2></div>{hazards.data && <span className="timestamp">Retrieved {time(hazards.data.fetchedAt)}</span>}</div>
    {hazards.loading && <p className="loading" role="status">Retrieving hazard evidence…</p>}
    {hazards.error && <div className="error" role="alert">{hazards.error}<button onClick={onRetry}>Retry observations</button></div>}
    {!hasQuery && <div className="empty">Your exploration starts with a location.<span>Observed events and advisories will appear here with sources and distance.</span></div>}
    {hazards.data && <div className="provider-coverage" aria-label="Provider coverage">
      <strong>Selected source coverage: {hazards.data.coverageStatus ?? 'UNKNOWN'}</strong>
      <p>{hazards.data.coverage}</p>
      {hazards.data.providerStatuses?.map(status => <details key={status.provider} open={status.coverageStatus !== 'COMPLETE'}>
        <summary>{status.provider}: {status.coverageStatus}</summary>
        <p>{status.retrievedAt ? `Retrieved ${time(status.retrievedAt)}` : 'No successful retrieval for this query.'}</p>
        <ul>{status.limitations.map(note => <li key={note}>{note}</li>)}</ul>
      </details>)}
      {unavailable && <p role="alert">Hazard data is unavailable. No assessment of hazard absence can be made.</p>}
      {hazards.data.providerStatuses?.some(s => s.coverageStatus === 'UNAVAILABLE') && <button onClick={onRetry}>Retry observations</button>}
    </div>}
    {hazards.data && !unavailable && items.length === 0 && <div className="empty">No matching hazards in the retrieved evidence.<span>This does not establish safety or rule out other hazards.</span></div>}
    {hazards.data?.truncated && <p className="notice">Source results were truncated. More events may exist; the retrieved events are not necessarily the nearest. Check provider coverage details.</p>}
    <div className="event-grid">{items.map(item => {
      const e = item.event; const id = `${e.source}:${e.externalId}`; const layer = hazardLayers[eventLayer(e)];
      return <article className={`event-card ${selected === id ? 'selected' : ''}`} key={id}>
        <div className="section-heading"><span className="magnitude" style={{ color: layer.color }}>{layer.symbol} {e.hazardType === 'EARTHQUAKE' ? `M ${e.measurements.magnitude?.toFixed(1) ?? '—'}` : e.source === 'FIRMS' ? 'Thermal anomaly' : e.hazardType.replaceAll('_', ' ')}</span><span className="tag">{informationLabel(e.informationType)}</span></div>
        <button className="event-title" onClick={() => onSelect(id)}>{e.title}</button>
        <p>{hazardLabel(e)}</p><p>{item.distanceKm.toFixed(1)} km away · {time(e.startedAt)}</p>
        <div className="event-meta"><span>Severity: {e.severity.toLowerCase()}<br/>Confidence: {e.confidence ?? 'UNKNOWN'}</span><a href={safeSourceUrl(e.sourceUrl)} target="_blank" rel="noreferrer">{e.source} evidence ↗</a></div>
        <details><summary>Provenance and measurements</summary><dl className="provenance">
          <dt>Provider event identity</dt><dd>{e.externalId}</dd>
          <dt>Observed / event start</dt><dd>{time(e.observedAt ?? e.startedAt)}</dd>
          <dt>Source updated</dt><dd>{e.sourceUpdatedAt ? time(e.sourceUpdatedAt) : 'Not supplied'}</dd>
          <dt>Retrieved</dt><dd>{time(e.retrievedAt ?? hazards.data!.fetchedAt)}</dd>
          {Object.entries(e.attributes ?? {}).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}
          {Object.entries(e.measurements).map(([key, value]) => <div key={key}><dt>{key}{key.startsWith('bright_') ? ' · K' : key === 'frp' ? ' · MW' : key === 'scan' || key === 'track' ? ' · km' : ''}</dt><dd>{value}</dd></div>)}
        </dl></details>
      </article>;
    })}</div>
    {hazards.data && <div className="source-attribution">{hazards.data.sources.map(source => <p key={source.id}><a href={safeSourceUrl(source.documentationUrl)} target="_blank" rel="noreferrer">{source.attribution}</a> · <a href={safeSourceUrl(source.licensingUrl)} target="_blank" rel="noreferrer">Terms and attribution</a><br/>{source.refreshPolicy}</p>)}</div>}
  </section>;
}
