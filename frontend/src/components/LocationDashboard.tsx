"use client";
import Brand from './layout/Brand';
import { hazardLayers, eventLayer } from '../lib/maps/hazardLayers';
import FeatureNav from './layout/FeatureNav';
import dynamic from 'next/dynamic';
import WeatherPanel from './weather/WeatherPanel';
import RiskSummary from './risk/RiskSummary';
import DisasterList from './disaster/DisasterList';
import { useState, type FormEvent } from 'react';
import { useLocationAssessment } from '../hooks/useLocationAssessment';
import { querySchema, type Query, type HazardItem } from '../types/hazard';
const Map = dynamic(() => import('./map/DisasterMap'), { ssr: false, loading: () => <div className="map-loading">Loading world map…</div> });
const EMPTY: HazardItem[] = [];
export default function LocationDashboard() {
  const { query, hazards, risk, weather, load } = useLocationAssessment();
  const [latitude, setLatitude] = useState(''); const [longitude, setLongitude] = useState('');
  const [radius, setRadius] = useState('100'); const [days, setDays] = useState('7');
  const [validation, setValidation] = useState('');
  const [layers, setLayers] = useState({ earthquakes: true, thermal: true, global: true });
  const [selected, setSelected] = useState<string | null>(null);
  function choose(next: Query) {
    setLatitude(next.latitude.toFixed(4)); setLongitude(next.longitude.toFixed(4));
    setValidation(''); setSelected(null); load(next);
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    const parsed = querySchema.safeParse({ latitude: Number(latitude), longitude: Number(longitude), radiusKm: Number(radius), days: Number(days) });
    if (!latitude.trim() || !longitude.trim() || !parsed.success) { setValidation('Enter valid coordinates, a radius of 1–1,000 km, and a lookback of 1–30 days.'); return; }
    choose(parsed.data);
  }
  function pick(lat: number, lon: number) {
    const parsed = querySchema.safeParse({ latitude: lat, longitude: lon, radiusKm: Number(radius), days: Number(days) });
    if (parsed.success) choose(parsed.data); else setValidation('Set a valid radius (1–1,000 km) and lookback (1–30 days) before choosing a point.');
  }
  const items = hazards.data?.items ?? EMPTY;
  return <div className="app-shell">
    <header className="masthead"><Brand/><span className="edition">FIELD VIEW <span>02 / MULTI-HAZARD</span></span></header>
    <FeatureNav/>
    <main>
      <div className="intro"><div><p className="eyebrow">UNDERSTAND THE GROUND AHEAD</p><h1>A clearer view of your surroundings.</h1><p>Explore earthquake observations, satellite thermal anomalies and global disaster advisories.</p></div><div className="coverage"><span className="dot" /> USGS · NASA FIRMS · GDACS<br/><small>Source coverage and freshness vary</small></div></div>
      <section className="workspace" aria-label="Location explorer">
        <aside className="search-panel"><p className="eyebrow">01 — CHOOSE A LOCATION</p><h2>Where are you headed?</h2><p className="muted">Enter coordinates or select a point on the map.</p>
          <form onSubmit={submit}><div className="field-pair"><label>Latitude<input name="latitude" type="number" step="any" min="-90" max="90" required value={latitude} onChange={e => setLatitude(e.target.value)} placeholder="32.2400" /></label><label>Longitude<input name="longitude" type="number" step="any" min="-180" max="180" required value={longitude} onChange={e => setLongitude(e.target.value)} placeholder="77.1900" /></label></div>
            <div className="field-pair"><label>Radius · km<input type="number" step="any" min="1" max="1000" required value={radius} onChange={e => setRadius(e.target.value)} /></label><label>Lookback · days<input type="number" min="1" max="30" required value={days} onChange={e => setDays(e.target.value)} /></label></div>
            {validation && <p className="error" role="alert">{validation}</p>}<button className="primary" type="submit">Explore location <span>↗</span></button>
          </form>
          <p className="eyebrow presets-label">QUICK LOCATIONS</p><div className="presets">{[['Manali', 32.24, 77.19], ['San Francisco', 37.7749, -122.4194], ['Tokyo', 35.6762, 139.6503]].map(([name, lat, lon]) => <button key={name} onClick={() => pick(Number(lat), Number(lon))}>{name} ↗</button>)}</div>
          <div className="layer-card"><p className="eyebrow">MAP LAYERS</p>{Object.entries(hazardLayers).map(([key, layer]) => <label className="layer-toggle" key={key}><input type="checkbox" aria-label={layer.label} checked={layers[key as keyof typeof layers]} onChange={e => setLayers(previous => ({ ...previous, [key]: e.target.checked }))} /><span aria-hidden="true" style={{ color: layer.color }}>{layer.symbol}</span> {layer.label}</label>)}<p>Controls affect the map; all retrieved evidence remains listed below.</p></div>
          <div className="scope-note"><strong>Observations, with context.</strong><p>Markers represent reported events—not active emergency warnings or predictions.</p></div>
        </aside>
        <div className="map-frame"><Map query={query} items={items.filter(item => layers[eventLayer(item.event)])} visible={true} selected={selected} onSelect={setSelected} onPick={pick} /><div className="map-caption"><span className="crosshair">⊕</span>{query ? <div><strong>{query.latitude.toFixed(4)}, {query.longitude.toFixed(4)}</strong><span>{query.radiusKm} km radius · past {query.days} days</span></div> : <div><strong>Select a point to begin</strong><span>Pan and zoom to explore the world</span></div>}</div></div>
        <RiskSummary risk={risk} hasQuery={!!query} onRetry={() => query && load(query)} />
      </section>
      <WeatherPanel weather={weather} hasQuery={!!query} onRetry={() => query && load(query)} />
      <DisasterList hazards={hazards} hasQuery={!!query} selected={selected} onSelect={setSelected} onRetry={() => query && load(query)} />
    </main><footer><strong>Custerra / Evidence before certainty.</strong><p>Not an official emergency-warning authority. Follow local government and emergency instructions. Unknown or low activity does not establish travel safety.</p><span>Data: <a href="https://earthquake.usgs.gov/fdsnws/event/1/">U.S. Geological Survey</a> · <a href="https://firms.modaps.eosdis.nasa.gov/">NASA FIRMS</a> · <a href="https://www.gdacs.org/">Global Disaster Awareness and Coordination System, GDACS</a></span></footer>
  </div>;
}
