'use client';
import { useState } from 'react';
import type { Intelligence } from '../../types/intelligence';
import { formatTimestamp as time } from '../../lib/utils/dateUtils';
export default function IntelligenceList({data,onSelect,selected}:{data:Intelligence;onSelect:(id:string)=>void;selected?:string|null}) {
  const [limit,setLimit]=useState(50);
  const items = new Map(data.items.map(item => [`${item.event.source}:${item.event.id}`, item]));
  const references = data.correlations.flatMap(group => {
    const linked = group.sourceIdentities.map(id => items.get(id)).filter(item => item !== undefined);
    return linked.length === group.sourceIdentities.length && linked.length > 1 ? [linked] : [];
  });
  return <section className="intelligence-list"><div className="section-heading"><h2>Official alerts & curated context</h2><span className="timestamp">{data.coverageStatus} · retrieved {time(data.retrievedAt)}</span></div><div className="provider-coverage">{data.sources.map(source=><details key={source.source} open={source.coverageStatus==='UNAVAILABLE'}><summary>{source.source}: {source.coverageStatus}</summary><p>{source.retrievedAt?`Retrieved ${time(source.retrievedAt)}`:'No successful retrieval.'}</p>{source.limitations.map(note=><p key={note}>{note}</p>)}</details>)}</div>{data.items.length===0 && <p className="empty">No localized items in this bounded response. Zone-only warnings may lack geometry; check the available-feed timeline and original sources. Empty evidence does not establish safety.</p>}<div className="intelligence-rows">{data.items.slice(0,limit).map(({event,state,spatial})=><article className={`intelligence-row ${selected===`INTELLIGENCE:${event.source}:${event.id}`?'selected':''}`} key={`${event.source}:${event.id}`}><div><span className="eyebrow">{event.source} / {event.informationType.replaceAll('_',' ')}</span><button className="event-title" onClick={()=>onSelect(`INTELLIGENCE:${event.source}:${event.id}`)}>{event.title}</button><p>{state} · source severity {event.severity} · {event.hazardType.replaceAll('_',' ')}</p></div><div className="intelligence-row-meta"><span>Issued / observed {time(event.issuedAt)}</span><span>Updated {time(event.updatedAt)}</span><span>{spatial?.intersects?'Intersects source geometry':spatial?.distanceKm!=null?`${spatial.distanceKm.toFixed(1)} km from geometry`:'Area relevance not assessed'}</span><span>{event.geometry?.type ?? 'Area text only'}</span></div></article>)}</div>{data.items.length>limit&&<button onClick={()=>setLimit(limit+50)}>Show more source records</button>}{references.length > 0 && <details className="source-revisions"><summary>Linked source revisions ({references.length})</summary>
    <p>The issuing source explicitly linked these records. Select a record to inspect its own evidence.</p>
    <ol>{references.map((group, index) => <li key={index}><ul>{group.map(({ event, state }) => <li key={event.id}>
      <button className="event-title" onClick={() => onSelect(`INTELLIGENCE:${event.source}:${event.id}`)}>{event.title}</button>
      <span className="timestamp"> {event.messageType} · {state} · {time(event.issuedAt)}</span>
    </li>)}</ul></li>)}</ol>
  </details>}{data.limitations.map(note=><p className="timestamp" key={note}>{note}</p>)}</section>;
}
