"use client";
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from '../../lib/api/journeyApi';
import { journeySchema, type Journey } from '../../types/journey';

export default function JourneyProgressPanel({ journey, onChange }: { journey: Journey; onChange: (journey: Journey) => void }) {
  const [error,setError] = useState(''), [busy,setBusy] = useState(false), [now,setNow] = useState(() => Date.now());
  const generation = useRef(0);
  useEffect(() => { const requestGeneration=generation; const timer=setInterval(() => setNow(Date.now()),30000); return () => { clearInterval(timer); requestGeneration.current++; }; }, []);
  const progress=journey.progress;
  const reliable=progress?.state === 'MATCHED' && now-Date.parse(progress.observedAt)<=300000;
  async function send(latitude: number, longitude: number, accuracyMeters: number, observedAt: string, version: number) {
    try {
      const updated=await api(`journeys/${journey.id}/progress`,journeySchema,'PUT',{position:{latitude,longitude},accuracyMeters,observedAt});
      if(version===generation.current)onChange(updated);
    } catch(e) { if(version===generation.current)setError(e instanceof Error?e.message:'Position update failed.'); }
    finally { if(version===generation.current)setBusy(false); }
  }
  function locate() {
    setError('');
    if(!navigator.geolocation){setError('Location access is unavailable. You can enter a position or monitor the full route.');return;}
    setBusy(true);const version=++generation.current;
    navigator.geolocation.getCurrentPosition(position => {
      if(version===generation.current)void send(position.coords.latitude,position.coords.longitude,position.coords.accuracy,new Date(position.timestamp).toISOString(),version);
    },()=>{if(version===generation.current){setError('Location permission was denied or a reading was unavailable. Full-route monitoring remains available.');setBusy(false);}},
    {enableHighAccuracy:true,maximumAge:0,timeout:15000});
  }
  function manual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form=new FormData(event.currentTarget);setError('');setBusy(true);
    void send(Number(form.get('latitude')),Number(form.get('longitude')),Number(form.get('accuracy')),new Date().toISOString(),++generation.current);
  }
  return <section aria-label="Journey progress"><h3>Optional current position</h3>
    <p>{reliable ? `Remaining-route monitoring starts at segment ${progress.segmentIndex+1} (at least ${Math.floor(progress.completedFraction*100)}% of route passed).` : 'Full-route monitoring: no recent, accurate route-matched position is available.'} The full matched segment is retained; this is an estimate, not navigation.</p>
    {progress && <p>Position recorded {progress.observedAt} · accuracy ±{progress.accuracyMeters.toFixed(0)} m · {now-Date.parse(progress.observedAt)>300000?'STALE':progress.state}. A location expires for monitoring after five minutes.</p>}
    <p>Only share your location if you want progress-aware checks. Each action sends one position; there is no continuous tracking. The server retains only the latest position, hides it after 24 hours and removes expired positions during its next hourly cleanup. Pausing, completing, cancelling or clearing removes it immediately. Offline copies omit your position.</p>
    <div className="feature-actions"><button disabled={busy || journey.status!=='ACTIVE'} onClick={locate}>Share current location once</button>
    <button disabled={busy || !progress} onClick={async()=>{setBusy(true);setError('');try{onChange(await api(`journeys/${journey.id}/progress`,journeySchema,'DELETE'));}catch(e){setError(e instanceof Error?e.message:'Could not clear position.');}finally{setBusy(false);}}}>Clear shared position</button></div>
    <details><summary>Enter current position manually</summary><form onSubmit={manual}><fieldset disabled={busy || journey.status!=='ACTIVE'}><div className="feature-fields">
      <label>Current latitude<input name="latitude" type="number" min="-90" max="90" step="any" required/></label>
      <label>Current longitude<input name="longitude" type="number" min="-180" max="180" step="any" required/></label>
      <label>Position accuracy · meters<input name="accuracy" type="number" min="0" max="100000" step="any" required/></label>
    </div><button>Share entered position once</button></fieldset></form></details>
    {busy && <p role="status">Updating position…</p>}{error && <p role="alert">{error}</p>}
  </section>;
}
