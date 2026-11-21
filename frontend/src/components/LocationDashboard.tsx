'use client';
import { useMemo, useRef, useState, type FormEvent } from 'react';
import dynamic from 'next/dynamic';
import WeatherPanel from './weather/WeatherPanel';
import RiskSummary from './risk/RiskSummary';
import DisasterList from './disaster/DisasterList';
import PlaceSearch from './route/PlaceSearch';
import Icon from './common/Icon';
import Link from 'next/link';
import IntelligenceDetails from './map/IntelligenceDetails';
import IntelligenceList from './map/IntelligenceList';
import type { IntelligenceItem } from '../types/intelligence';
import { hazardLayers, eventLayer, informationLabel } from '../lib/maps/hazardLayers';
import { useLocationAssessment } from '../hooks/useLocationAssessment';
import { querySchema, safeSourceUrl, type Query, type HazardItem } from '../types/hazard';
import { formatTimestamp } from '../lib/utils/dateUtils';
const Map = dynamic(() => import('./map/DisasterMap'), { ssr: false, loading: () => <div className="map-loading" role="status"><span>Loading world map…</span></div> });
const EMPTY: HazardItem[] = [];
const NO_INTELLIGENCE:IntelligenceItem[]=[];
export default function LocationDashboard() {
  const { query, hazards, risk, weather, intelligence, intelligenceLayers, setIntelligenceLayers, load, selected, setSelected, layers, setLayers } = useLocationAssessment();
  const [latitude, setLatitude] = useState(query?.latitude.toFixed(4) ?? ''); const [longitude, setLongitude] = useState(query?.longitude.toFixed(4) ?? '');
  const [radius, setRadius] = useState(String(query?.radiusKm ?? 100)); const [days, setDays] = useState(String(query?.days ?? 7));
  const [validation, setValidation] = useState('');
  const [tab, setTab] = useState(selected ? 'evidence' : 'context');
  const [sheetOpen, setSheetOpen] = useState(true);
  const collapseInspector = useRef<HTMLButtonElement>(null);
  const expandInspector = useRef<HTMLButtonElement>(null);

  function toggleDesktopInspector(open: boolean) {
    setSheetOpen(open);
    requestAnimationFrame(() => (open ? collapseInspector : expandInspector).current?.focus());
  }
  const [searchOpen,setSearchOpen]=useState(!query);
  function choose(next: Query) { setLatitude(next.latitude.toFixed(4)); setLongitude(next.longitude.toFixed(4)); setValidation(''); setSelected(null); setSearchOpen(false); load(next); }
  function submit(event: FormEvent) { event.preventDefault(); const parsed = querySchema.safeParse({ latitude: Number(latitude), longitude: Number(longitude), radiusKm: Number(radius), days: Number(days) }); if (!latitude.trim() || !longitude.trim() || !parsed.success) { setValidation('Enter valid coordinates, a radius of 1–1,000 km, and a lookback of 1–30 days.'); return; } choose(parsed.data); }
  function pick(lat: number, lon: number) { const parsed = querySchema.safeParse({ latitude: lat, longitude: lon, radiusKm: Number(radius), days: Number(days) }); if (parsed.success) choose(parsed.data); else setValidation('Set a valid radius (1–1,000 km) and lookback (1–30 days) before choosing a point.'); }
  function inspect(id: string) { setSelected(id); setTab('evidence'); setSheetOpen(true); requestAnimationFrame(()=>{if(window.matchMedia('(max-width: 760px)').matches)document.getElementById('location-inspector')?.scrollIntoView({block:'nearest'});}); }
  const items = hazards.data?.items ?? EMPTY;
  const visibleItems = useMemo(() => items.filter(item => layers[eventLayer(item.event)]), [items, layers]);
  const intelligenceItems=intelligence.data?.items??NO_INTELLIGENCE;
  const visibleIntelligence=useMemo(()=>intelligenceItems.filter(item=>intelligenceLayers[item.event.source]),[intelligenceItems,intelligenceLayers]);
  const selectedIntelligence=intelligenceItems.find(item=>`INTELLIGENCE:${item.event.source}:${item.event.id}`===selected);
  const event = items.find(item => `${item.event.source}:${item.event.externalId}` === selected);
  return <main className="explorer-page">
    <div className="page-heading"><div><p className="eyebrow">GEOGRAPHIC INTELLIGENCE</p><h1>Explore the evidence<span className="brand-period">.</span></h1></div><div className="heading-context"><span className="status-dot"/>On-demand observations<span className="muted">No continuous polling</span></div></div>
    <section className={`explore-stage ${sheetOpen ? '' : 'inspector-closed'}`} aria-label="Location explorer">
      <div className="map-frame">
        {!sheetOpen && <button ref={expandInspector} className="desktop-inspector-toggle" aria-controls="location-inspector" aria-expanded={false} onClick={() => toggleDesktopInspector(true)}><Icon name="layers" size={16}/> Show evidence panel</button>}
        <Map query={query} items={visibleItems} intelligenceItems={visibleIntelligence} visible selected={selected} onSelect={inspect} onPick={pick}/><div className="map-caption"><Icon name="explore" size={19}/>{query ? <div><strong>{query.latitude.toFixed(4)}, {query.longitude.toFixed(4)}</strong><span>{query.radiusKm} km radius · past {query.days} days</span></div> : <div><strong>Select a point to begin</strong><span>Click the map or search a location</span></div>}<span className="map-caption-label">OVERALL RISK · UNKNOWN</span></div></div>
      <aside className={`explore-search ${searchOpen?'search-expanded':'search-collapsed'}`}><div className="panel-heading"><Icon name="search" size={17}/><h2>Location search</h2></div><button className="mobile-search-toggle" aria-expanded={searchOpen} aria-controls="location-controls" onClick={()=>setSearchOpen(!searchOpen)}>{searchOpen?'Collapse location controls':'Edit location & layers'}</button><div id="location-controls"><details className="place-disclosure"><summary>Find a town or city</summary><PlaceSearch label="Place" onSelect={place => { if (place) pick(place.location.latitude, place.location.longitude); }}/></details>
        <form onSubmit={submit}><div className="field-pair"><label>Latitude<input name="latitude" type="number" step="any" min="-90" max="90" required value={latitude} onChange={e => setLatitude(e.target.value)} placeholder="32.2400"/></label><label>Longitude<input name="longitude" type="number" step="any" min="-180" max="180" required value={longitude} onChange={e => setLongitude(e.target.value)} placeholder="77.1900"/></label></div><div className="field-pair"><label>Radius · km<input type="number" min="1" max="1000" step="any" required value={radius} onChange={e => setRadius(e.target.value)}/></label><label>Lookback · days<input type="number" min="1" max="30" required value={days} onChange={e => setDays(e.target.value)}/></label></div>{validation && <p className="error" role="alert">{validation}</p>}<button className="primary" type="submit">Explore location <Icon name="arrow" size={16}/></button></form>
        <div className="presets" aria-label="Quick locations">{[['Manali', 32.24, 77.19], ['San Francisco', 37.7749, -122.4194], ['Tokyo', 35.6762, 139.6503]].map(([name, lat, lon]) => <button key={name} onClick={() => pick(Number(lat), Number(lon))}>{name}<Icon name="arrow" size={11}/></button>)}</div>
        <details className="layer-card" open><summary><Icon name="layers" size={15}/>Evidence layers</summary>{Object.entries(hazardLayers).map(([key, layer]) => <label className="layer-toggle" key={key}><input type="checkbox" aria-label={layer.label} checked={layers[key as keyof typeof layers]} onChange={e => setLayers(previous => ({ ...previous, [key]: e.target.checked }))}/><span aria-hidden="true" style={{ color: layer.color }}>{layer.symbol}</span><span>{layer.label}</span></label>)}{(['NWS','EONET'] as const).map(source=><label className="layer-toggle" key={source}><input type="checkbox" checked={intelligenceLayers[source]} onChange={e=>setIntelligenceLayers(previous=>({...previous,[source]:e.target.checked}))}/><span>{source==='NWS'?'Official alerts · California':'EONET · curated context'}</span></label>)}<p>FIRMS requires configured access. Layer visibility does not change the evidence or assessment.</p></details>
      </div></aside>
      <aside id="location-inspector" className={`context-inspector ${sheetOpen?'sheet-expanded':'sheet-collapsed'}`} aria-label="Location inspection"><button className="sheet-handle" aria-expanded={sheetOpen} aria-controls="location-inspector-content" onClick={()=>setSheetOpen(!sheetOpen)}><span aria-hidden="true"/>{sheetOpen?'Collapse evidence panel':'Expand evidence panel'}</button><div id="location-inspector-content" className="inspector-content">
        <div className="inspector-heading"><h2>Location evidence</h2><button ref={collapseInspector} className="icon-button" aria-label="Hide evidence panel" title="Hide evidence panel" aria-controls="location-inspector" aria-expanded={true} onClick={() => toggleDesktopInspector(false)}><Icon name="close" size={17}/></button></div>
        <div className="inspector-tabs" role="tablist" aria-label="Location panels">{[['context','Context'],['evidence','Selection'],['alerts','Alerts'],['weather','Weather']].map(([id,label]) => <button key={id} id={`panel-tab-${id}`} role="tab" aria-selected={tab === id} aria-controls={`panel-${id}`} tabIndex={tab === id ? 0 : -1} onKeyDown={e=>{if(['ArrowLeft','ArrowRight','Home','End'].includes(e.key)){e.preventDefault();const ids=['context','evidence','alerts','weather'];const index=ids.indexOf(tab);const next=e.key==='Home'?0:e.key==='End'?3:(index+(e.key==='ArrowRight'?1:3))%4;setTab(ids[next]);document.getElementById(`panel-tab-${ids[next]}`)?.focus();}}} onClick={() => setTab(id)}>{label}</button>)}</div>
        <div className="inspector-body"><div id="panel-context" role="tabpanel" aria-labelledby="panel-tab-context" hidden={tab !== 'context'}><RiskSummary risk={risk} hasQuery={!!query} onRetry={() => query && load(query)}/></div>
          <div id="panel-evidence" role="tabpanel" aria-labelledby="panel-tab-evidence" hidden={tab !== 'evidence'}>{selectedIntelligence?<IntelligenceDetails key={selectedIntelligence.event.id+selectedIntelligence.event.updatedAt} item={selectedIntelligence}/>:event ? <section className="selected-evidence"><p className="eyebrow">SOURCE EVIDENCE</p><span className="tag">{informationLabel(event.event.informationType)}</span><h2>{event.event.title}</h2><p>{event.event.hazardType.replaceAll('_', ' ')} · {event.event.source}</p><dl className="inspection-data"><div><dt>Magnitude</dt><dd>{event.event.measurements.magnitude ?? 'Not supplied'}</dd></div><div><dt>Distance</dt><dd>{event.distanceKm.toFixed(1)} km</dd></div><div><dt>Coordinates</dt><dd>{event.event.latitude.toFixed(4)}, {event.event.longitude.toFixed(4)}</dd></div><div><dt>Provider severity</dt><dd>{event.event.severity}</dd></div><div><dt>Source confidence</dt><dd>{event.event.confidence ?? 'UNKNOWN'}</dd></div><div><dt>Event time</dt><dd>{formatTimestamp(event.event.startedAt)}</dd></div><div><dt>Source update</dt><dd>{event.event.sourceUpdatedAt ? formatTimestamp(event.event.sourceUpdatedAt) : 'Not supplied'}</dd></div><div><dt>Retrieved</dt><dd>{formatTimestamp(event.event.retrievedAt ?? hazards.data!.fetchedAt)}</dd></div></dl><a className="text-link" href={safeSourceUrl(event.event.sourceUrl)} target="_blank" rel="noreferrer">Original source <Icon name="external" size={14}/></a><p className="scope-note">A reported point is not an affected area. Source severity is not local travel risk.</p></section> : <div className="inspector-empty"><Icon name="explore" size={28}/><h2>Take a closer look</h2><p>Select a marker or an evidence item to inspect its source, measurements and timestamps.</p></div>}</div>
          <div id="panel-alerts" role="tabpanel" aria-labelledby="panel-tab-alerts" hidden={tab !== 'alerts'}><div className="selected-evidence"><h2>Official & curated sources</h2><p>NWS official alert coverage is California only. EONET supplies approximate curated event context. Zone-only alerts cannot be localized without geometry.</p>{intelligence.loading&&<p role="status">Retrieving source alerts…</p>}{intelligence.error&&<p role="alert">{intelligence.error}</p>}{intelligence.data?.sources.map(source=><details key={source.source} open><summary>{source.source}: {source.coverageStatus}</summary>{source.limitations.map(note=><p key={note}>{note}</p>)}</details>)}<a className="text-link" href="#intelligence-evidence">Inspect localized source records ↓</a><p><Link href="/insights">Browse the available-feed timeline ↗</Link></p></div></div>
          <div id="panel-weather" role="tabpanel" aria-labelledby="panel-tab-weather" hidden={tab !== 'weather'}><WeatherPanel weather={weather} hasQuery={!!query} onRetry={() => query && load(query)}/></div></div>
        <div className="inspector-footer"><Icon name="info" size={14}/><span>Source coverage varies. Unknown never means safe.</span></div></div>
      </aside>
    </section>
    <section id="intelligence-evidence">{intelligence.loading&&<p role="status">Retrieving source alerts…</p>}{intelligence.error&&<p className="error" role="alert">Source alerts unavailable. {intelligence.error}</p>}{intelligence.data&&<IntelligenceList data={intelligence.data} onSelect={inspect} selected={selected}/>}</section>
    <DisasterList hazards={hazards} hasQuery={!!query} selected={selected} onSelect={inspect} onRetry={() => query && load(query)}/>
  </main>;
}
