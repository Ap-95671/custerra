"use client";
import { useEffect, useRef, useState } from 'react';
import { api } from '../../lib/api/journeyApi';
import { placesSchema, type Place } from '../../types/planning';
import { safeSourceUrl } from '../../types/hazard';
import type { PlaceDraft } from '../layout/NavigationDrafts';

type Props = { label: string } & (
  { onSelect: (place: Place | null) => void; draft?: never; onDraftChange?: never }
  | { draft: PlaceDraft; onDraftChange: (next: PlaceDraft) => void; onSelect?: never }
);
export default function PlaceSearch({ label, onSelect, draft, onDraftChange }: Props) {
  const [local, setLocal] = useState<PlaceDraft>({ text: '', place: null });
  const { text, place: selected } = draft ?? local;
  const [result, setResult] = useState<ReturnType<typeof placesSchema.parse> | null>(null);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);
  function update(next: PlaceDraft) {
    if (onDraftChange) onDraftChange(next);
    else { setLocal(next); onSelect(next.place); }
  }
  function edit(value: string) {
    active.current?.abort(); setBusy(false); setResult(null); setError(''); update({ text: value, place: null });
  }
  async function search() {
    active.current?.abort(); const controller = new AbortController(); active.current = controller;
    setBusy(true); setError(''); setResult(null); update({ text, place: null });
    try { const found = await api(`geocoding/search?q=${encodeURIComponent(text.trim())}`, placesSchema, 'GET', undefined, controller.signal); if (!controller.signal.aborted) setResult(found); }
    catch (e) { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Place search failed.'); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  return <div className="place-search"><label>{label}<input value={text} onChange={e => edit(e.target.value)} maxLength={120} placeholder={label === 'From' ? 'Delhi, India' : 'Manali, India'} /></label>
    <button type="button" disabled={busy || text.trim().length < 2} onClick={() => void search()}>Search {label.toLowerCase()}</button>
    {busy && <p role="status">Searching places…</p>}{error && <p role="alert">{error}</p>}
    {!result && selected && <p className="place-credit">Selected {label.toLowerCase()} · {[selected.name, selected.region, selected.country].filter(Boolean).join(', ')}</p>}
    {result && <><p className="place-credit">Places: <a href={safeSourceUrl(result.source.documentationUrl)}>{result.source.attribution}</a> · retrieved {result.retrievedAt}</p>
      {result.candidates.length === 0 ? <p>No matching places. Try a nearby town or a country qualifier.</p> : <fieldset><legend>Choose {label.toLowerCase()}</legend>{result.candidates.map(place => <label key={place.id} className="place-choice"><input type="radio" name={`place-${label}`} checked={selected?.id === place.id} onChange={() => update({ text, place })} /><span>{[place.name, place.region, place.country].filter(Boolean).join(', ')}<small>{place.location.latitude.toFixed(4)}, {place.location.longitude.toFixed(4)}</small></span></label>)}</fieldset>}</>}
  </div>;
}
