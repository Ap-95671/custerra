"use client";
import { useEffect, useRef, useState } from 'react';
import dynamic from 'next/dynamic';
import PlaceSearch from './PlaceSearch';
import { api } from '../../lib/api/journeyApi';
import { plannedRouteSchema, type Place, type PlannedRoute } from '../../types/planning';
import { safeSourceUrl } from '../../types/hazard';
const Map = dynamic(() => import('./RouteMap'), { ssr: false, loading: () => <p>Loading route map…</p> });
export default function RoutePlanner() {
  const [origin, setOrigin] = useState<Place | null>(null), [destination, setDestination] = useState<Place | null>(null);
  const [corridor, setCorridor] = useState('25'), [days, setDays] = useState('7'), [magnitude, setMagnitude] = useState('4');
  const [result, setResult] = useState<PlannedRoute | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);
  function invalidate() { active.current?.abort(); setBusy(false); setResult(null); setError(''); }
  async function analyze() {
    if (!origin || !destination) return;
    active.current?.abort(); const controller = new AbortController(); active.current = controller;
    setBusy(true); setResult(null); setError('');
    try {
      const data = await api('routes/plan', plannedRouteSchema, 'POST', { origin: origin.location, destination: destination.location, mode: 'DRIVING', corridorKm: Number(corridor), days: Number(days), minMagnitude: Number(magnitude) }, controller.signal);
      if (!controller.signal.aborted) setResult(data);
    } catch (e) { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Route planning failed.'); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  return <section className="feature-card" aria-label="Driving route planner"><h2>From place to place</h2><p>Search for a town or city, then choose the matching place. Provider routes may snap endpoints to nearby roads. Routes up to 900 km are supported.</p>
    <div className="feature-fields"><PlaceSearch label="From" onSelect={place => { invalidate(); setOrigin(place); }} /><PlaceSearch label="To" onSelect={place => { invalidate(); setDestination(place); }} /></div>
    <form onSubmit={e => { e.preventDefault(); void analyze(); }}><div className="feature-fields">
      <label>Travel mode<select value="DRIVING" disabled><option value="DRIVING">Driving</option></select></label>
      <label>Route corridor · km<input type="number" min="1" max="100" required value={corridor} onChange={e => { invalidate(); setCorridor(e.target.value); }} /></label>
      <label>Route lookback · days<input type="number" min="1" max="30" required value={days} onChange={e => { invalidate(); setDays(e.target.value); }} /></label>
      <label>Route minimum magnitude<input type="number" min="0" max="10" step="0.1" required value={magnitude} onChange={e => { invalidate(); setMagnitude(e.target.value); }} /></label>
    </div><button disabled={busy || !origin || !destination}>Analyze route</button></form>
    {busy && <p role="status">Retrieving the driving route and earthquake evidence…</p>}{error && <p role="alert">{error}</p>}
    {result && <section aria-label="Driving route result"><h3>{result.distanceKm.toFixed(1)} km driving route</h3><p>Estimated duration: {Math.round(result.durationSeconds / 60)} minutes · retrieved {result.retrievedAt}</p><Map route={result} />
      <p>Route: <a href={safeSourceUrl(result.source.documentationUrl)}>{result.source.attribution}</a> · <a href={safeSourceUrl(result.source.licensingUrl)}>Provider terms</a> · <a href="https://www.openstreetmap.org/fixthemap">Fix the map</a></p>
      {result.assessmentError && <p role="alert">Hazard evidence unavailable ({result.assessmentError}). The route is displayed, but exposure is unknown. Retry Analyze route.</p>}
      {result.assessment && <><h3>Route exposure · {result.assessment.overallRisk}</h3><p>{result.assessment.explanation}</p>{result.assessment.truncated && <p role="status">Incomplete source coverage; matching events may be missing.</p>}
        <p>Assessed {result.assessment.assessedAt} · {result.assessment.modelVersion}</p><ul>{result.assessment.matches.map(match => <li key={`${match.event.source}:${match.event.externalId}`}><a href={safeSourceUrl(match.event.sourceUrl)}>{match.event.title}</a> · {match.distanceKm.toFixed(1)} km from segment {match.segmentIndex + 1} · {match.event.startedAt}</li>)}</ul>
        {result.assessment.sources.map(source => <p key={source.id}>Evidence: <a href={safeSourceUrl(source.documentationUrl)}>{source.attribution}</a></p>)}</>}
      <ul>{result.limitations.map(note => <li key={note}>{note}</li>)}</ul>
    </section>}
  </section>;
}
