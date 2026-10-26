"use client";
import { useEffect, useState, type FormEvent } from 'react';
import { saveJourneyOffline, removeJourneyOffline } from '../../offline/cacheManager';
import { z } from 'zod';
import RoutePlanner from './RoutePlanner';
import FeatureNav from '../layout/FeatureNav';
import { SessionGate } from '../auth/SessionProvider';
import { api } from '../../lib/api/journeyApi';
import { assessmentSchema, journeySchema, noticesSchema, parsePoints, routeInputSchema, type Assessment, type Journey, type Notice } from '../../types/journey';
import { safeSourceUrl } from '../../types/hazard';
function AssessmentView({ data }: { data: Assessment }) {
  const groups = Object.groupBy(data.matches, match => match.event.hazardType ?? 'EARTHQUAKE');
  return <section aria-label="Route assessment"><h2>Route context · {data.overallRisk}</h2><p>{data.lengthKm.toFixed(1)} km of supplied geometry · assessed {data.assessedAt}</p>
    <p>Data coverage: {data.coverageStatus ?? (data.truncated ? 'TRUNCATED' : 'UNKNOWN')}</p>
    {data.truncated && <p role="status">Incomplete catalog coverage. Matching events may be missing; notices from truncated providers are withheld.</p>}
    <p>{data.explanation}</p>
    {data.providerStatuses?.map(source => <p key={source.provider}>{source.provider} · {source.coverageStatus} · retrieved {source.retrievedAt ?? 'Unavailable'}<br/>{source.limitations.join(' · ')}</p>)}
    <h3>Hazard breakdown</h3><p>These are retrieved event counts, not hazard risk classifications. Unrepresented hazards remain UNKNOWN.</p>
    <ul>{Object.entries(groups).map(([hazard, matches]) => <li key={hazard}>{hazard === 'WILDFIRE' ? 'WILDFIRE / thermal anomalies' : hazard}: {matches?.length ?? 0} matching evidence items · risk UNKNOWN</li>)}</ul>
    <h3>{data.matches.length} matching observations</h3><ul>{data.matches.map(match => <li key={`${match.event.source}:${match.event.externalId}`}>
      <a href={safeSourceUrl(match.event.sourceUrl)}>{match.event.title}</a> · {match.event.hazardType ?? 'EARTHQUAKE'} · {match.event.informationType ?? 'OBSERVED_EVENT'} · {match.event.source}<br/>
      {match.distanceKm.toFixed(1)} km from segment {match.segmentIndex + 1} · {match.event.startedAt} · severity {match.event.severity ?? 'UNKNOWN'} · confidence {match.event.confidence ?? 'UNKNOWN'}<br/>
      Provider update: {match.event.sourceUpdatedAt ?? 'Unknown'} · retrieved: {match.event.retrievedAt ?? 'Unknown'}</li>)}</ul>
    {data.sources?.map(source => <p key={source.id}><a href={safeSourceUrl(source.documentationUrl)}>{source.attribution}</a></p>)}
  </section>;
}
function JourneyWorkspace() {
  const [journeys, setJourneys] = useState<Journey[]>([]); const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Journey | null>(null); const [notices, setNotices] = useState<Notice[]>([]);
  const [noticePage, setNoticePage] = useState(0); const [preview, setPreview] = useState<Assessment | null>(null);
  const [offlineMessage, setOfflineMessage] = useState('');
  const [error, setError] = useState(''); const [busy, setBusy] = useState(true);
  const [monitoring, setMonitoring] = useState<{ schedulerEnabled: boolean; intervalMs: number } | null>(null);
  useEffect(() => { const controller = new AbortController();
    Promise.all([api('journeys', z.array(journeySchema), 'GET', undefined, controller.signal), api('journeys/monitoring', z.object({ schedulerEnabled: z.boolean(), intervalMs: z.number() }), 'GET', undefined, controller.signal)]).then(([items, config]) => { setJourneys(items); setMonitoring(config); }).catch(e => { if (!controller.signal.aborted) setError(e.message); }).finally(() => { if (!controller.signal.aborted) setBusy(false); });
    return () => controller.abort();
  }, []);
  async function run(action: () => Promise<void>) { setBusy(true); setError(''); setOfflineMessage(''); try { await action(); } catch (e) { setError(e instanceof z.ZodError ? 'Enter 2–20 valid coordinate pairs and check the corridor, lookback and magnitude limits.' : e instanceof Error ? e.message : 'Request failed.'); } finally { setBusy(false); } }
  async function list(next = page) { setJourneys(await api(`journeys?page=${next}`, z.array(journeySchema))); setPage(next); }
  async function select(journey: Journey) { setSelected(journey); setNotices([]); setNoticePage(0); setNotices(await api(`journeys/${journey.id}/notices`, noticesSchema)); }
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget); const action = (e.nativeEvent as SubmitEvent).submitter?.getAttribute('value');
    void run(async () => {
      const route = routeInputSchema.parse({ points: parsePoints(String(f.get('points'))), corridorKm: Number(f.get('corridor')), days: Number(f.get('days')), minMagnitude: Number(f.get('magnitude')) });
      if (action === 'save') { const saved = await api('journeys', journeySchema, 'POST', { name: f.get('name'), route }); await select(saved); await list(0); }
      else { setPreview(null); setPreview(await api('routes/assessment', assessmentSchema, 'POST', route)); }
    });
  }
  async function change(status: string) { if (selected) { await select(await api(`journeys/${selected.id}`, journeySchema, 'PATCH', { status })); await list(); } }
  return <><details><summary>Manual waypoint mode</summary><p>Enter 2–20 ordered waypoints. Segments follow the shortest great-circle path between points; they are not driving directions. Total length must be 0.001–900 km.</p>
    <form className="feature-card" onSubmit={submit}><fieldset disabled={busy}><legend>Route geometry and observation filters</legend><label>Journey name<input name="name" required maxLength={120}/></label><label>Waypoints · latitude, longitude per line<textarea name="points" rows={5} required placeholder={'32.24, 77.19\n32.30, 77.25'}/></label><div className="feature-fields"><label>Corridor · km<input name="corridor" type="number" min="1" max="100" defaultValue="25" required/></label><label>Lookback · days<input name="days" type="number" min="1" max="30" defaultValue="7" required/></label><label>Minimum magnitude<input name="magnitude" type="number" min="0" max="10" step="0.1" defaultValue="4" required/></label></div><div className="feature-actions"><button value="assess">Assess route</button><button value="save">Save paused journey</button></div></fieldset></form></details>
    {offlineMessage && <p role="status">{offlineMessage}</p>}{busy && <p role="status">Working…</p>}{error && <p role="alert">{error}</p>}{preview && <div className="feature-card"><AssessmentView data={preview}/></div>}
    <section className="feature-card"><h2>Saved journeys</h2><p>{monitoring ? monitoring.schedulerEnabled ? `Scheduled checks enabled, every ${monitoring.intervalMs / 60000} minutes, up to 10 due journeys per cycle.` : 'Scheduled checks are disabled on this server. Use Check now for active journeys.' : 'Scheduler configuration unavailable.'}</p><p>Journeys are private to your signed-in account. Activate a journey, then check it manually or enable the server scheduler. The first usable check establishes a separate baseline for each provider. Later checks create notices for new or meaningfully changed evidence. Complete queries can identify evidence that no longer matches; partial feeds cannot confirm disappearance. No email or push notifications are sent.</p><div className="feature-actions"><button disabled={busy} onClick={() => void run(() => list())}>Reload journeys</button><button disabled={busy || page === 0} onClick={() => void run(() => list(page-1))}>Previous journeys</button><button disabled={busy || journeys.length < 50} onClick={() => void run(() => list(page+1))}>Next journeys</button></div><ul>{journeys.map(j => <li key={j.id}><button disabled={busy} onClick={() => void run(() => select(j))}>{j.name} · {j.status}</button></li>)}</ul>{journeys.length === 0 && <p>No saved journeys on this page.</p>}</section>
    {selected && <section className="feature-card" aria-label="Selected journey"><h2>{selected.name}</h2><p>Status: {selected.status} · corridor {selected.route.corridorKm} km · magnitude ≥ {selected.route.minMagnitude} · past {selected.route.days} days</p><details><summary>Saved waypoints</summary><ol>{selected.route.points.map((point, index) => <li key={index}>{point.latitude}, {point.longitude}</li>)}</ol></details><p>Last attempt: {selected.lastAttemptAt ?? 'Never'}<br/>Last complete check: {selected.lastCheckedAt ?? 'No baseline yet'}</p>{selected.providerCheckpoints?.map(checkpoint => <p key={checkpoint.provider}>{checkpoint.provider} last successful evidence check: {checkpoint.lastSuccessfulCheck}</p>)}{selected.lastError && <p role="alert">Check status: {selected.lastError}. Previous evidence may be out of date.</p>}<div className="feature-actions"><button disabled={busy} onClick={() => void run(() => change(selected.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'))}>{selected.status === 'ACTIVE' ? 'Pause journey' : 'Activate journey'}</button><button disabled={busy || selected.status !== 'ACTIVE'} onClick={() => void run(async () => { await select(await api(`journeys/${selected.id}/check`, journeySchema, 'POST')); await list(); })}>Check now</button><button disabled={busy} onClick={() => void run(async () => { await api(`journeys/${selected.id}`, z.null(), 'DELETE'); try { removeJourneyOffline(selected.id); } catch { setOfflineMessage('Journey deleted on server, but its offline copy could not be removed. Clear it on the Offline copies page.'); } setSelected(null); setNotices([]); await list(); })}>Delete saved journey</button></div>
    <p>Save a read-only copy of this journey, its assessment and the latest 50 notices on this browser for up to seven days. Copies may be out of date and are visible to anyone using this browser profile, including after session expiry. Signing out removes all local journey copies.</p><button disabled={busy} onClick={() => void run(async () => { const fresh = await api(`journeys/${selected.id}`, journeySchema); const latest = await api(`journeys/${selected.id}/notices`, noticesSchema); await saveJourneyOffline(fresh, latest); setOfflineMessage('Offline copy saved. Open Offline copies to review it before departure.'); })}>Save offline copy</button>
    {selected.assessment && <AssessmentView data={selected.assessment}/>}<h3>Informational notices</h3>{notices.length === 0 && <p>No notices on this page. This does not establish safe travel conditions.</p>}<ul>{notices.map(n => <li key={n.id}><a href={safeSourceUrl(n.evidence.event.sourceUrl)}>{n.evidence.event.title}</a> · {n.type} · {n.evidence.event.source} · {n.evidence.event.informationType ?? 'OBSERVED_EVENT'} · {n.createdAt}<p>{n.explanation}</p></li>)}</ul><div className="feature-actions"><button disabled={busy || noticePage === 0} onClick={() => void run(async () => { setNotices(await api(`journeys/${selected.id}/notices?page=${noticePage-1}`, noticesSchema)); setNoticePage(noticePage-1); })}>Previous notices</button><button disabled={busy || notices.length < 50} onClick={() => void run(async () => { setNotices(await api(`journeys/${selected.id}/notices?page=${noticePage+1}`, noticesSchema)); setNoticePage(noticePage+1); })}>Next notices</button></div></section>}
    <p>Monitoring covers USGS observations, FIRMS thermal anomalies and GDACS advisories near stored route segments. It uses reported points, not affected-area intersection or live GPS. Fixed lookback windows, provider outages and feed revisions can miss changes. Overall route risk remains unknown.</p>
  </>;
}
export default function JourneyExplorer() {
  return <div className="app-shell"><FeatureNav/><main className="feature-page"><p className="eyebrow">JOURNEY MONITORING</p><h1>Evidence along your route.</h1><RoutePlanner/><SessionGate><JourneyWorkspace/></SessionGate></main></div>;
}
