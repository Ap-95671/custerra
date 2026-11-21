"use client";
import { useEffect, useRef, useState } from 'react';
import dynamic from 'next/dynamic';
import PlaceSearch from './PlaceSearch';
import Icon from '../common/Icon';
import AreaExposurePanel from './AreaExposurePanel';
import RoadClosuresPanel from './RoadClosuresPanel';
import type { RouteExposure } from '../../types/intelligence';
import { useSession } from '../auth/SessionProvider';
import { useRouteDraft } from '../layout/NavigationDrafts';
import { journeySchema, type Journey } from '../../types/journey';
import { api } from '../../lib/api/journeyApi';
import { plannedRouteSchema, alternativesSchema, type PlannedRoute } from '../../types/planning';
import { safeSourceUrl } from '../../types/hazard';
const Map = dynamic(() => import('./RouteMap'), { ssr: false, loading: () => <p>Loading route map…</p> });
export default function RoutePlanner({ onSaved }: { onSaved?: (journey: Journey) => void }) {
  const session = useSession();
  const [draft, setDraft] = useRouteDraft();
  const { origin: { place: origin }, destination: { place: destination }, corridor, days, magnitude } = draft;
  const [saving, setSaving] = useState(false), [saved, setSaved] = useState(false);
  const [result, setResult] = useState<PlannedRoute | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const [alternatives,setAlternatives]=useState<PlannedRoute[]>([]);const [areaExposure,setAreaExposure]=useState<RouteExposure|null>(null);
  const [selectedSegment,setSelectedSegment]=useState<number|null>(null);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);
  function invalidate() { active.current?.abort(); setBusy(false); setResult(null); setError(''); setSaved(false); setSelectedSegment(null);setAlternatives([]);setAreaExposure(null); }
  function editDraft(update: Partial<typeof draft>) {
    invalidate();
    setDraft(previous => ({ ...previous, ...update }));
  }
  async function analyze(compare=false) {
    if (!origin || !destination) return;
    active.current?.abort(); const controller = new AbortController(); active.current = controller;
    setBusy(true); setResult(null); setError(''); setSaved(false);
    try {
      const payload={ origin: origin.location, destination: destination.location, mode: 'DRIVING', corridorKm: Number(corridor), days: Number(days), minMagnitude: Number(magnitude) };
      if(compare){const data=await api('routes/alternatives',alternativesSchema,'POST',payload,controller.signal);if(!controller.signal.aborted){setAlternatives(data.routes);setResult(data.routes[0]);setAreaExposure(null);}}
      else{const data=await api('routes/plan',plannedRouteSchema,'POST',payload,controller.signal);if(!controller.signal.aborted){setResult(data);setAlternatives([]);setAreaExposure(null);}}

    } catch (e) { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Route planning failed.'); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  async function save() {
    if (!result || !origin || !destination || saving) return;
    setSaving(true); setError('');
    try {
      const journey = await api('journeys/generated', journeySchema, 'POST', {
        name: `${origin.name} to ${destination.name}`.slice(0, 120), originName: origin.name.slice(0,120), destinationName: destination.name.slice(0,120),
        geometry: result.geometry, corridorKm: Number(corridor), days: Number(days), minMagnitude: Number(magnitude),
      });
      setSaved(true); onSaved?.(journey);
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not save journey.'); }
    finally { setSaving(false); }
  }
  return <section className="feature-card route-planner" aria-label="Driving route planner"><div className="planner-layout"><div className="planner-controls"><h2>From place to place</h2><p>Search for a town or city, then choose the matching place. Provider routes may snap endpoints to nearby roads. Routes up to 900 km are supported.</p>
    <div className="feature-fields"><PlaceSearch label="From" draft={draft.origin} onDraftChange={place => editDraft({ origin: place })} /><PlaceSearch label="To" draft={draft.destination} onDraftChange={place => editDraft({ destination: place })} /></div>
    <form onSubmit={e => { e.preventDefault(); void analyze(); }}><div className="feature-fields">
      <label>Travel mode<select value="DRIVING" disabled><option value="DRIVING">Driving</option></select></label>
      <label>Route corridor · km<input type="number" min="1" max="100" required value={corridor} onChange={e => editDraft({ corridor: e.target.value })} /></label>
      <label>Route lookback · days<input type="number" min="1" max="30" required value={days} onChange={e => editDraft({ days: e.target.value })} /></label>
      <label>Route minimum magnitude<input type="number" min="0" max="10" step="0.1" required value={magnitude} onChange={e => editDraft({ magnitude: e.target.value })} /></label>
    </div><button disabled={busy || !origin || !destination}>Analyze route</button><button type="button" disabled={busy||!origin||!destination} onClick={()=>void analyze(true)}>Compare provider routes</button></form>
    {busy && <p role="status">Retrieving the driving route and earthquake evidence…</p>}{error && <p role="alert">{error}</p>}
    </div>{!result && <div className="planner-empty"><Icon name="route" size={44}/><p className="eyebrow">FROM HERE TO THERE</p><h3>Put your route in perspective.</h3><p>Choose an origin and destination to see real driving geometry and the earthquake evidence around it.</p><div className="empty-route-line"><span>A</span><i/><span>B</span></div><small>No route calculated · Overall exposure UNKNOWN</small></div>}
    {result && <section className="planner-result" aria-label="Driving route result"><h3>{result.distanceKm.toFixed(1)} km driving route</h3><p>Estimated duration: {Math.round(result.durationSeconds / 60)} minutes · retrieved {result.retrievedAt}</p>{alternatives.length>0&&<><div className="route-alternatives">{alternatives.map((route,index)=><button key={route.geometry} aria-pressed={result.geometry===route.geometry} onClick={()=>{setResult(route);setSelectedSegment(null);setAreaExposure(null);setSaved(false);}}>Route {index+1} · {route.distanceKm.toFixed(1)} km · {Math.round(route.durationSeconds/60)} min · {route.assessment?`${route.assessment.matches.length} retrieved proximity records`:'Evidence unavailable'}</button>)}</div><p>Provider returned {alternatives.length} route(s). No route is ranked as safer. Counts use separately timestamped queries.</p></>}
      <Map route={result} areaExposure={areaExposure} selectedSegment={selectedSegment} onSelectSegment={setSelectedSegment}/>{selectedSegment!==null && <p role="status">Inspecting original segment {selectedSegment+1}. Proximity does not establish physical impact.</p>}
      <p>Route: <a href={safeSourceUrl(result.source.documentationUrl)}>{result.source.attribution}</a> · <a href={safeSourceUrl(result.source.licensingUrl)}>Provider terms</a> · <a href="https://www.openstreetmap.org/fixthemap">Fix the map</a></p>
      {result.assessmentError && <p role="alert">Hazard evidence unavailable ({result.assessmentError}). The route is displayed, but exposure is unknown. Retry Analyze route.</p>}
      {result.assessment && <><h3>Route exposure · {result.assessment.overallRisk}</h3><p>{result.assessment.explanation}</p>{result.assessment.truncated && <p role="status">Incomplete source coverage; matching events may be missing.</p>}
        <p>Assessed {result.assessment.assessedAt} · {result.assessment.modelVersion}</p><ul>{result.assessment.matches.map(match => <li key={`${match.event.source}:${match.event.externalId}`}><button type="button" aria-pressed={selectedSegment===match.segmentIndex} onClick={()=>setSelectedSegment(match.segmentIndex)}>Inspect segment {match.segmentIndex+1}</button> <a href={safeSourceUrl(match.event.sourceUrl)}>{match.event.title}</a> · {match.distanceKm.toFixed(1)} km from segment {match.segmentIndex + 1} · {match.event.startedAt}</li>)}</ul>
        {result.assessment.sources.map(source => <p key={source.id}>Evidence: <a href={safeSourceUrl(source.documentationUrl)}>{source.attribution}</a></p>)}</>}
      {session.user ? <button disabled={saving || saved} onClick={() => void save()}>{saving ? 'Saving journey…' : saved ? 'Journey saved' : 'Save Journey'}</button> : <p><a href="/login">Sign in</a> to save and monitor this route.</p>}
      {saved && <p role="status">Journey saved as PLANNED. Start it in Saved journeys below. Location sharing is optional.</p>}
      <AreaExposurePanel key={`areas:${result.geometry}`} geometry={result.geometry} onChange={setAreaExposure} onSelectSegment={setSelectedSegment}/>
      <RoadClosuresPanel key={`closures:${result.geometry}`} route={{ geometry: result.geometry }} onSelectSegment={setSelectedSegment}/>
      <ul>{result.limitations.map(note => <li key={note}>{note}</li>)}</ul>
    </section>}
  </div></section>;
}
