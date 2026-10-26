"use client";
import { useEffect, useState } from 'react';
import { z } from 'zod';
import { api } from '../../lib/api/journeyApi';
import { noticesSchema } from '../../types/journey';
import { safeSourceUrl } from '../../types/hazard';
import FeatureNav from '../layout/FeatureNav';
import { SessionGate } from '../auth/SessionProvider';
const hazards=['EARTHQUAKE','FLOOD','LANDSLIDE','WILDFIRE','TSUNAMI','CYCLONE','HURRICANE','TYPHOON','TORNADO','VOLCANIC_ERUPTION','SEVERE_WEATHER'] as const;
const levels=['UNKNOWN','INFO','LOW','MODERATE','HIGH','CRITICAL'] as const;
const preferencesSchema=z.object({ enabled:z.boolean(),pushEnabled:z.boolean(),hazardTypes:z.array(z.enum(hazards)),minimumSeverity:z.enum(levels),includeUnknown:z.boolean() });
const itemSchema=z.object({ id:z.string().uuid(),journeyName:z.string(),notice:noticesSchema.element,readAt:z.string().nullable(),deliveries:z.array(z.object({ channel:z.string(),status:z.string(),attempts:z.number(),lastError:z.string().nullable() })) });
const pushSchema=z.object({ configured:z.boolean(),dispatcherEnabled:z.boolean(),publicKey:z.string().nullable() });
type Item=z.infer<typeof itemSchema>;type Preferences=z.infer<typeof preferencesSchema>;
function Inbox() {
  const [items,setItems]=useState<Item[]>([]);const [detail,setDetail]=useState<Item|null>(null);const [page,setPage]=useState(0);
  const [preferences,setPreferences]=useState<Preferences|null>(null);const [push,setPush]=useState<z.infer<typeof pushSchema>|null>(null);
  const [busy,setBusy]=useState(true);const [error,setError]=useState('');const [message,setMessage]=useState('');
  useEffect(()=>{ const controller=new AbortController();
    const selected=new URLSearchParams(window.location.search).get('notice');
    Promise.all([api('notifications',z.array(itemSchema),'GET',undefined,controller.signal),api('notifications/preferences',preferencesSchema,'GET',undefined,controller.signal),api('notifications/push',pushSchema,'GET',undefined,controller.signal),
      selected && /^[0-9a-f-]{36}$/i.test(selected) ? api(`notifications/${selected}`,itemSchema,'GET',undefined,controller.signal) : Promise.resolve(null)])
      .then(([list,pref,config,item])=>{setItems(list);setPreferences(pref);setPush(config);setDetail(item);}).catch(e=>{if(!controller.signal.aborted)setError(e.message);}).finally(()=>{if(!controller.signal.aborted)setBusy(false);});
    return()=>controller.abort();
  },[]);
  async function run(action:()=>Promise<void>) { setBusy(true);setError('');setMessage('');try{await action();}catch(e){setError(e instanceof Error?e.message:'Notification request failed.');}finally{setBusy(false);} }
  async function load(next:number) { setItems(await api(`notifications?page=${next}`,z.array(itemSchema)));setPage(next); }
  async function save(next:Preferences) { setPreferences(await api('notifications/preferences',preferencesSchema,'PATCH',next));setMessage('Notification preferences saved. Changes apply to future notices and pending delivery.'); }
  async function enablePush() {
    if(!push?.publicKey || !preferences) return;
    if(!window.isSecureContext || !('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) throw new Error('Web push needs a supported browser on HTTPS or localhost.');
    if(await Notification.requestPermission()!=='granted') throw new Error('Browser notification permission was not granted. You can change it in browser settings.');
    const registration=await navigator.serviceWorker.register('/journey-sw.js',{scope:'/',updateViaCache:'none'});await navigator.serviceWorker.ready;
    const old=await registration.pushManager.getSubscription();
    if(old) { await api('notifications/subscriptions/remove',z.null(),'POST',{endpoint:old.endpoint});await old.unsubscribe(); }
    const key=Uint8Array.from(atob(push.publicKey.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));
    const subscription=await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:key});
    const exported=subscription.toJSON();
    try { await api('notifications/subscriptions',z.object({id:z.string().uuid()}),'POST',{endpoint:subscription.endpoint,p256dh:exported.keys?.p256dh,auth:exported.keys?.auth}); }
    catch(e) { await subscription.unsubscribe();throw e; }
    await save({...preferences,pushEnabled:true});setMessage('This browser is subscribed. Future eligible journey notices can be sent while dispatch is enabled.');
  }
  async function disablePush() {
    const registration='serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration('/') : undefined;
    const subscription=await registration?.pushManager.getSubscription();
    if(subscription) { await api('notifications/subscriptions/remove',z.null(),'POST',{endpoint:subscription.endpoint});await subscription.unsubscribe(); }
    setMessage('Push subscription removed for this browser. Other subscribed browsers are unchanged.');
  }
  function card(item:Item) { const event=item.notice.evidence.event;return <article className="feature-card" key={item.id}><h3>{item.journeyName}</h3><p>{item.readAt?'READ':'UNREAD'} · {item.notice.type} · {item.notice.createdAt}</p>
    <p>{event.hazardType ?? 'EARTHQUAKE'} · {event.informationType ?? 'OBSERVED_EVENT'} · {event.source} · provider severity {event.severity ?? 'UNKNOWN'}</p>
    <a href={safeSourceUrl(event.sourceUrl)}>{event.title}</a><p>{item.notice.explanation}</p><p>{item.notice.evidence.distanceKm.toFixed(1)} km from segment {item.notice.evidence.segmentIndex+1}</p>
    {item.deliveries.map((delivery,index)=><p key={index}>{delivery.channel}: {delivery.status} · attempts {delivery.attempts}{delivery.lastError?` · ${delivery.lastError}`:''}</p>)}
    {!item.readAt && <button disabled={busy} onClick={()=>void run(async()=>{await api(`notifications/${item.id}/read`,z.null(),'POST');await load(page);if(detail?.id===item.id)setDetail(await api(`notifications/${item.id}`,itemSchema));})}>Mark read</button>}</article>; }
  return <><h1>Journey notifications</h1><p>Informational changes in retrieved evidence, not emergency orders. SENT means a push service accepted the message; it does not confirm device delivery. UNCERTAIN outcomes are not automatically resent.</p>
    {busy && <p role="status">Working…</p>}{error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    {preferences && <form className="feature-card" onSubmit={e=>{e.preventDefault();void run(()=>save(preferences));}}><fieldset disabled={busy}><legend>Notification preferences</legend>
      <label><input type="checkbox" checked={preferences.enabled} onChange={e=>setPreferences({...preferences,enabled:e.target.checked})}/>Create notifications for future journey notices</label>
      <label><input type="checkbox" checked={preferences.pushEnabled} onChange={e=>setPreferences({...preferences,pushEnabled:e.target.checked})}/>Allow web push to subscribed browsers</label>
      <label>Minimum provider severity<select value={preferences.minimumSeverity} onChange={e=>setPreferences({...preferences,minimumSeverity:e.target.value as Preferences['minimumSeverity']})}>{levels.map(level=><option key={level}>{level}</option>)}</select></label>
      <label><input type="checkbox" checked={preferences.includeUnknown} onChange={e=>setPreferences({...preferences,includeUnknown:e.target.checked})}/>Include evidence with UNKNOWN severity</label>
      <p>Severity is the source event classification, not local danger. USGS and thermal observations commonly have UNKNOWN severity.</p>
      <fieldset><legend>Hazard families</legend>{hazards.map(hazard=><label key={hazard}><input type="checkbox" checked={preferences.hazardTypes.includes(hazard)} onChange={e=>setPreferences({...preferences,hazardTypes:e.target.checked?[...preferences.hazardTypes,hazard]:preferences.hazardTypes.filter(h=>h!==hazard)})}/>{hazard==='WILDFIRE'?'WILDFIRE / thermal anomalies':hazard}</label>)}</fieldset>
      <button>Save preferences</button></fieldset></form>}
    <section className="feature-card"><h2>Browser delivery</h2><p>{push?.configured?'Web push keys are configured.':'Web push is unavailable until the server operator configures VAPID keys and a contact URI.'} {push?.dispatcherEnabled?'Dispatch is enabled.':'Dispatch is disabled on this server.'}</p>
      <p>Opting in may show journey names and source event details on your lock screen. Signing out revokes subscriptions registered in that session; messages already accepted by a push service may still arrive.</p>
      <button disabled={busy||!push?.configured||!push.dispatcherEnabled} onClick={()=>void run(enablePush)}>Enable this browser</button> <button disabled={busy} onClick={()=>void run(disablePush)}>Disable this browser</button></section>
    {detail && <section aria-label="Selected notification"><h2>Selected notification</h2>{card(detail)}</section>}
    <section aria-label="Notification inbox"><h2>Recent notifications</h2>{items.map(card)}{!items.length && <p>No notifications on this page. This does not establish safe travel conditions.</p>}
      <button disabled={busy||page===0} onClick={()=>void run(()=>load(page-1))}>Previous notifications</button> <button disabled={busy||items.length<50} onClick={()=>void run(()=>load(page+1))}>Next notifications</button> <button disabled={busy} onClick={()=>void run(()=>load(page))}>Reload notifications</button></section></>;
}
export default function NotificationCenter() { return <div className="app-shell"><FeatureNav/><main className="feature-page"><SessionGate><Inbox/></SessionGate></main></div>; }
