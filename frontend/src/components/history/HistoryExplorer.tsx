"use client";
import { useState, type FormEvent } from 'react';
import FeatureNav from '../layout/FeatureNav';
import { SessionGate } from '../auth/SessionProvider';
import { api } from '../../lib/api/journeyApi';
import { historySchema, type History } from '../../types/history';
import { safeSourceUrl } from '../../types/hazard';
function HistoryWorkspace() {
  const [data, setData] = useState<History | null>(null);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [id, setId] = useState('');
  async function run(action: () => Promise<History>) {
    setBusy(true); setError(''); setData(null);
    try { const result = await action(); setData(result); setId(result.id); }
    catch (e) { setError(e instanceof Error ? e.message : 'Request failed.'); }
    finally { setBusy(false); }
  }
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    const body = { latitude: Number(f.get('latitude')), longitude: Number(f.get('longitude')), radiusKm: Number(f.get('radius')), minMagnitude: Number(f.get('magnitude')), from: `${f.get('from')}T00:00:00Z`, to: `${f.get('to')}T00:00:00Z` };
    void run(() => api('history/earthquakes', historySchema, 'POST', body));
  }
  return <><p className="eyebrow">HISTORICAL EVIDENCE</p><h1>Earthquake exposure over time.</h1><p>Count catalog events within a radius and magnitude threshold. Each result is saved as a reproducible snapshot. Observed frequency is not a forecast.</p>
    <form className="feature-card" onSubmit={submit}><fieldset disabled={busy}><legend>Historical query</legend><div className="feature-fields">
      <label>Latitude<input name="latitude" type="number" step="any" min="-90" max="90" required/></label><label>Longitude<input name="longitude" type="number" step="any" min="-180" max="180" required/></label>
      <label>Radius · km<input name="radius" type="number" min="1" max="1000" defaultValue="100" required/></label><label>Minimum magnitude<input name="magnitude" type="number" min="0" max="10" step="0.1" defaultValue="4" required/></label>
      <label>From · UTC, inclusive<input name="from" type="date" min="1900-01-01" required/></label><label>To · UTC, exclusive<input name="to" type="date" min="1900-01-02" required/></label>
    </div><p>Use a past window of 1–3,660 days. At most 2,000 events are retained; incomplete results withhold frequency.</p><button type="submit">Retrieve & save history</button></fieldset></form>
    <form className="feature-card" onSubmit={e => { e.preventDefault(); if (/^[0-9a-f-]{36}$/i.test(id)) void run(() => api(`history/earthquakes/${id}`, historySchema)); else setError('Enter a valid snapshot ID.'); }}><label>Saved snapshot ID<input required value={id} onChange={e => setId(e.target.value)}/></label><button disabled={busy}>Load snapshot</button></form>
    {busy && <p role="status">Retrieving historical evidence…</p>}{error && <p role="alert">{error}</p>}
    {data && <section className="feature-card" aria-label="Historical result"><h2>{data.truncated ? 'At least ' : ''}{data.eventCount} matching earthquakes</h2><p>Observed annual frequency: <strong>{data.annualFrequency === null ? 'Unavailable — incomplete catalog response' : `${data.annualFrequency.toFixed(2)} events/year`}</strong></p><p>{data.query.from} to {data.query.to} · {data.query.radiusKm} km · magnitude ≥ {data.query.minMagnitude}</p><p>{data.explanation}</p><p>Snapshot <code>{data.id}</code> · {data.modelVersion} · retrieved {data.fetchedAt}</p><a href={safeSourceUrl(data.source.documentationUrl)}>{data.source.attribution}</a><details><summary>Stored event evidence ({data.events.length})</summary><ul>{data.events.map(event => <li key={`${event.source}:${event.externalId}`}><a href={safeSourceUrl(event.sourceUrl)}>{event.title}</a> · {event.startedAt} · M {event.measurements.magnitude ?? 'unknown'}</li>)}</ul></details></section>}
  </>;
}
export default function HistoryExplorer() { return <div className="app-shell"><FeatureNav/><main className="feature-page"><SessionGate><HistoryWorkspace/></SessionGate></main></div>; }
