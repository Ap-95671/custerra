'use client';
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { z } from 'zod';
import dynamic from 'next/dynamic';
import { api } from '../../lib/api/journeyApi';
import { intelligenceSchema } from '../../types/intelligence';
import { useInsightDraft } from '../layout/NavigationDrafts';
import { useSession } from '../auth/SessionProvider';
import { formatTimestamp } from '../../lib/utils/dateUtils';
import { safeSourceUrl, nearbySchema } from '../../types/hazard';
import IntelligenceList from '../map/IntelligenceList';
import IntelligenceDetails from '../map/IntelligenceDetails';
const Map=dynamic(()=>import('./IntelligenceMap'),{ssr:false,loading:()=> <p role="status">Loading temporal map…</p>});
const historyEvidenceSchema = z.object({
  coverageStatus: z.enum(['RETRIEVED', 'TRUNCATED', 'UNAVAILABLE']),
  eventCount: z.number().int().nonnegative().nullable(),
  annualFrequency: z.number().nonnegative().nullable(),
  fetchedAt: z.string().nullable(),
  source: z.object({ attribution: z.string(), documentationUrl: z.string() }).nullable(),
});
const rainfallSchema = z.object({
  days: z.array(z.object({ date: z.string(), rainMm: z.number().nonnegative().nullable() })).length(7),
  totalRainMm: z.number().nonnegative().nullable(),
  incomplete: z.boolean(),
  gridLatitude: z.number(),
  gridLongitude: z.number(),
  retrievedAt: z.string(),
  source: z.string(),
  sourceUrl: z.string(),
});
const insightViews = [['timeline', 'Timeline'], ['compare', 'Compare places'], ['terrain', 'Geographic context']] as const;
const comparisonSchema=z.object({collectedAt:z.string(),limitations:z.array(z.string()),historicalWindow:z.object({from:z.string(),to:z.string(),minMagnitude:z.number()}).optional(),destinations:z.array(z.object({destination:z.object({name:z.string(),latitude:z.number(),longitude:z.number()}),earthquakes:nearbySchema,intelligence:intelligenceSchema,overallRisk:z.literal('UNKNOWN'),history:historyEvidenceSchema.optional()}))});
const elevationSchema=z.object({latitude:z.number(),longitude:z.number(),elevationMeters:z.number(),slope:z.object({degrees:z.number().nonnegative(),offsetMeters:z.number().positive()}).optional(),retrievedAt:z.string(),source:z.string(),sourceUrl:z.string(),limitations:z.array(z.string())});
export default function InsightsWorkspace() {
  const { user } = useSession();
  return <InsightsContent key={user?.id ?? 'guest'}/>;
}

function InsightsContent(){
  const [draft, setDraft] = useInsightDraft();
  const { view, includeHistory, data, selected, provider, hazard, classification, region, from, to, timeField } = draft;
  const [rainfall, setRainfall] = useState<z.infer<typeof rainfallSchema> | null>(null);const [comparison,setComparison]=useState<z.infer<typeof comparisonSchema>|null>(null);const [elevation,setElevation]=useState<z.infer<typeof elevationSchema>|null>(null);
  const [busy,setBusy]=useState(false);const [error,setError]=useState('');
  const activeRequest = useRef<AbortController | null>(null);
  useEffect(() => () => activeRequest.current?.abort(), []);

  async function run(action: (signal: AbortSignal) => Promise<void>) {
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    setBusy(true);
    setError('');
    try { await action(controller.signal); }
    catch (cause) {
      if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Intelligence request failed.');
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  function editDraft(update: Partial<typeof draft>) {
    setDraft(previous => ({ ...previous, ...update }));
  }

  function switchView(next: typeof view) {
    if (next === view) return;
    activeRequest.current?.abort();
    setBusy(false);
    editDraft({ view: next });
    setError('');
  }
  const filtered=useMemo(()=>data?{...data,items:data.items.filter(item=>(provider==='all'||item.event.source===provider)&&(hazard==='all'||item.event.hazardType===hazard)&&(classification==='all'||item.event.informationType===classification)&&(!region||`${item.event.title} ${item.event.areaDescription}`.toLowerCase().includes(region.toLowerCase()))&&(!from||Date.parse(item.event[timeField])>=Date.parse(`${from}T00:00:00Z`))&&(!to||Date.parse(item.event[timeField])<Date.parse(`${to}T00:00:00Z`)))}:null,[data,provider,hazard,classification,region,from,to,timeField]);
  const inspected=filtered?.items.find(item=>`${item.event.source}:${item.event.id}`===selected);
  function compare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const history = includeHistory ? {
      from: `${values.get('history-from')}T00:00:00Z`,
      to: `${values.get('history-to')}T00:00:00Z`,
      minMagnitude: Number(values.get('history-magnitude')),
    } : undefined;
    if (history && history.from >= history.to) {
      setError('The end of the historical comparison must come after the start.');
      return;
    }
    setComparison(null);
    void run(async signal => setComparison(await api('intelligence/compare', comparisonSchema, 'POST', {
      destinations: [1, 2].map(index => ({ name: String(values.get(`name-${index}`)), latitude: Number(values.get(`latitude-${index}`)), longitude: Number(values.get(`longitude-${index}`)) })),
      radiusKm: Number(values.get('radius')),
      days: Number(values.get('days')),
      ...(history && { history }),
    }, signal)));
  }

  function terrain(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const coordinates = `latitude=${Number(form.get('latitude'))}&longitude=${Number(form.get('longitude'))}`;
    const action = (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value');
    if (action === 'rainfall') {
      setRainfall(null);
      void run(async signal => setRainfall(await api(`intelligence/rainfall?${coordinates}`, rainfallSchema, 'GET', undefined, signal)));
    } else {
      setElevation(null);
      void run(async signal => setElevation(await api(`intelligence/elevation?${coordinates}&includeSlope=true`, elevationSchema, 'GET', undefined, signal)));
    }
  }

  return <main className="feature-page"><div className="page-heading"><div><p className="eyebrow">EVIDENCE THROUGH TIME & PLACE</p><h1>A wider perspective.</h1></div></div>
    <div className="insights-tabs" role="tablist" aria-label="Intelligence views">
      {insightViews.map(([id, label], index) => <button key={id} role="tab" aria-selected={view === id} aria-controls={`insight-${id}`}
        id={`insight-tab-${id}`} tabIndex={view === id ? 0 : -1} onClick={() => switchView(id)}
        onKeyDown={event => {
          if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
          event.preventDefault();
          const last = insightViews.length - 1;
          const next = event.key === 'Home' ? 0 : event.key === 'End' ? last : (index + (event.key === 'ArrowRight' ? 1 : last)) % insightViews.length;
          const nextId = insightViews[next][0];
          switchView(nextId);
          document.getElementById(`insight-tab-${nextId}`)?.focus();
        }}>{label}</button>)}
    </div>
    {error && <p className="error" role="alert">{error}</p>}
    {busy && <p role="status">Retrieving bounded source evidence…</p>}
  <section id="insight-timeline" role="tabpanel" aria-labelledby="insight-tab-timeline" hidden={view!=='timeline'}><p>Explore available EONET curated events and NWS California products. These bounded feeds are not a complete global disaster archive. Observation/issue, update and retrieval timestamps are distinct.</p><button className="primary" disabled={busy} onClick={()=>void run(async signal => {const snapshot=await api('intelligence/timeline',intelligenceSchema,'GET',undefined,signal);if(!signal.aborted) editDraft({data:snapshot,selected:null});})}>Load available feeds</button>{data&&<><div className="timeline-controls"><label>Source<select value={provider} onChange={e=>editDraft({provider:e.target.value})}><option value="all">All available sources</option><option value="NWS">NWS · California</option><option value="EONET">EONET · curated global context</option></select></label><label>Hazard family<select value={hazard} onChange={e=>editDraft({hazard:e.target.value})}><option value="all">All available families</option>{[...new Set(data.items.map(i=>i.event.hazardType))].sort().map(h=><option key={h}>{h}</option>)}</select></label><label>Information type<select value={classification} onChange={e=>editDraft({classification:e.target.value})}><option value="all">All information types</option>{[...new Set(data.items.map(i=>i.event.informationType))].sort().map(t=><option key={t}>{t}</option>)}</select></label><label>Area/title contains<input value={region} onChange={e=>editDraft({region:e.target.value})} placeholder="Search source area text"/></label><label>Time meaning<select value={timeField} onChange={e=>editDraft({timeField:e.target.value as typeof timeField})}><option value="issuedAt">Issued / observed</option><option value="updatedAt">Source update</option><option value="retrievedAt">Retrieved</option></select></label><label>From · UTC inclusive<input type="date" value={from} onChange={e=>editDraft({from:e.target.value})}/></label><label>To · UTC exclusive<input type="date" value={to} onChange={e=>editDraft({to:e.target.value})}/></label></div><p className="timestamp">Date filters can hide still-active warnings issued before the window. These controls filter the retrieved snapshot and do not fetch providers.</p><div className="split-intelligence"><div><Map items={filtered!.items} selected={selected} onSelect={id=>editDraft({selected:id})}/><IntelligenceList data={filtered!} onSelect={id=>editDraft({selected:id.replace(/^INTELLIGENCE:/,'')})} selected={selected?`INTELLIGENCE:${selected}`:null}/></div>{inspected&&<IntelligenceDetails key={inspected.event.id+inspected.event.updatedAt} item={inspected}/>}</div></>}</section>
  <section id="insight-compare" role="tabpanel" aria-labelledby="insight-tab-compare" hidden={view!=='compare'}><p>Compare retrieved earthquake observations, official-alert geometry and curated context using the same filters. Coverage and timestamps remain visible; there is no universal safety ranking.</p><form className="feature-card" onSubmit={compare}><fieldset disabled={busy}><div className="feature-fields">{[1,2].map(index=><fieldset key={index}><legend>Destination {index}</legend><label>Destination {index} name<input name={`name-${index}`} maxLength={80} required/></label><div className="field-pair"><label>Destination {index} latitude<input name={`latitude-${index}`} type="number" min="-90" max="90" step="any" required/></label><label>Destination {index} longitude<input name={`longitude-${index}`} type="number" min="-180" max="180" step="any" required/></label></div></fieldset>)}<label>Comparison radius · km<input name="radius" type="number" min="1" max="1000" defaultValue="100" required/></label><label>Comparison lookback · days<input name="days" type="number" min="1" max="30" defaultValue="7" required/></label></div><label className="check-inline"><input type="checkbox" checked={includeHistory} onChange={event => editDraft({includeHistory:event.target.checked})}/> Include a historical USGS comparison</label>
    {includeHistory && <fieldset><legend>Historical earthquake window · UTC</legend><div className="feature-fields">
      <label>Comparison history from · inclusive<input name="history-from" type="date" min="1900-01-01" required/></label>
      <label>Comparison history to · exclusive<input name="history-to" type="date" min="1900-01-02" required/></label>
      <label>Comparison minimum magnitude<input name="history-magnitude" type="number" min="0" max="10" step="0.1" defaultValue="4" required/></label>
    </div><p className="timestamp">Choose a past window of 1–3,660 days. Both locations use the same time, radius and magnitude filters.</p></fieldset>}
    <button className="primary">Compare source evidence</button></fieldset></form>{comparison&&<>
    {comparison.historicalWindow && <p className="timestamp">
      Historical window, UTC · {comparison.historicalWindow.from.slice(0,10)} (inclusive) to {comparison.historicalWindow.to.slice(0,10)} (exclusive) · M ≥ {comparison.historicalWindow.minMagnitude}
    </p>}
    <div className="comparison-columns">{comparison.destinations.map((destination,index)=><section className="feature-card" key={index}><h2>{destination.destination.name}</h2><p>{destination.destination.latitude.toFixed(4)}, {destination.destination.longitude.toFixed(4)}</p><p className="comparison-count">{destination.earthquakes.items.length}<small> retrieved earthquake records</small></p><p>Earthquake coverage: {destination.earthquakes.coverageStatus??'UNKNOWN'} · {destination.earthquakes.fetchedAt}</p><p>{destination.intelligence.items.filter(i=>i.event.source==='NWS').length} localized NWS products · California scope only</p><p>{destination.intelligence.items.filter(i=>i.event.source==='EONET').length} curated EONET records</p><p>Intelligence coverage: {destination.intelligence.coverageStatus}</p>{destination.history && <section className="context-result" aria-label={`Historical USGS catalog · ${destination.destination.name}`}>
        <h3>Historical earthquake catalog</h3>
        {destination.history.eventCount === null ? <p>Historical coverage unavailable.</p> : <>
          <p>{destination.history.coverageStatus === 'TRUNCATED' ? 'At least ' : ''}{destination.history.eventCount} catalog records</p>
          <p>{destination.history.annualFrequency === null ? 'Observed frequency withheld due to incomplete results.' : `${destination.history.annualFrequency.toFixed(2)} observed events per year`}</p>
          <p className="timestamp">{destination.history.source && <a href={safeSourceUrl(destination.history.source.documentationUrl)}>{destination.history.source.attribution}</a>} · {destination.history.fetchedAt && formatTimestamp(destination.history.fetchedAt)}</p>
        </>}
      </section>}<strong>Overall risk: UNKNOWN</strong></section>)}</div>{comparison.limitations.map(note=><p className="timestamp" key={note}>{note}</p>)}</>}</section>
  <section id="insight-terrain" role="tabpanel" aria-labelledby="insight-tab-terrain" hidden={view!=='terrain'}><p>Retrieve modeled elevation and broad local slope, or a recent seven-day rainfall history. These contextual measurements do not establish flood, landslide or travel risk.</p><form className="feature-card" onSubmit={terrain}><fieldset disabled={busy}><div className="field-pair"><label>Terrain latitude<input name="latitude" type="number" min="-90" max="90" step="any" required/></label><label>Terrain longitude<input name="longitude" type="number" min="-180" max="180" step="any" required/></label></div><div className="feature-actions"><button value="elevation">Retrieve terrain context</button><button value="rainfall">Retrieve rainfall history</button></div></fieldset></form>{elevation&&<section className="feature-card"><h2>{elevation.elevationMeters.toFixed(0)} m · modeled elevation</h2><p>{elevation.source} · retrieved {elevation.retrievedAt}</p>{elevation.slope && <p>Modeled slope {elevation.slope.degrees.toFixed(1)}° · terrain sampled {elevation.slope.offsetMeters} m from the location in four directions.</p>}<a href={safeSourceUrl(elevation.sourceUrl)} target="_blank" rel="noreferrer">Source methodology ↗</a>{elevation.limitations.map(note=><p key={note}>{note}</p>)}</section>}
    {rainfall && <section className="feature-card" aria-label="Historical rainfall context">
      <h2>Seven-day modeled rainfall</h2>
      <p>{rainfall.incomplete || rainfall.totalRainMm === null ? 'Window total unavailable because at least one day is missing.' : `${rainfall.totalRainMm.toFixed(1)} mm during the retrieved window.`}</p>
      <p>Model grid {rainfall.gridLatitude.toFixed(2)}, {rainfall.gridLongitude.toFixed(2)} · {rainfall.source} · retrieved {formatTimestamp(rainfall.retrievedAt)} · <a href={safeSourceUrl(rainfall.sourceUrl)}>Source methodology</a></p>
      <dl className="rainfall-days">{rainfall.days.map(day => <div key={day.date}><dt>{day.date}</dt><dd>{day.rainMm === null ? 'Unavailable' : `${day.rainMm.toFixed(1)} mm`}</dd></div>)}</dl>
      <p className="timestamp">Daily rain totals are modeled on a grid in UTC. The archive is delayed. These values describe past conditions and do not forecast flooding or landslides.</p>
    </section>}</section></main>;
}
