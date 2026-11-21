'use client';
import { useEffect,useRef } from 'react';
import L from 'leaflet';
import type { Geometry,Feature } from 'geojson';
import type { IntelligenceItem } from '../../types/intelligence';
import { mapConfig } from '../../lib/maps/mapConfig';
import { reportedPositions, sourceGeometry } from '../../lib/maps/sourceGeometry';
export default function IntelligenceMap({items,selected,onSelect}:{items:IntelligenceItem[];selected:string|null;onSelect:(id:string)=>void}){
  const node=useRef<HTMLDivElement>(null);const map=useRef<L.Map|null>(null);const select=useRef(onSelect);
  useEffect(()=>{select.current=onSelect;},[onSelect]);
  useEffect(()=>{if(!node.current)return;const instance=L.map(node.current,{minZoom:2,worldCopyJump:true}).setView([22,0],2);map.current=instance;L.tileLayer(mapConfig.tileUrl,{attribution:mapConfig.attribution,maxZoom:19}).addTo(instance);const resize=new ResizeObserver(()=>instance.invalidateSize());resize.observe(node.current);return()=>{resize.disconnect();instance.remove();map.current=null;};},[]);
  useEffect(()=>{if(!map.current)return;const instance=map.current;const group=L.layerGroup().addTo(instance);const pinned=items.find(i=>`${i.event.source}:${i.event.id}`===selected);const ordered=pinned?[pinned,...items.filter(i=>i!==pinned)]:items;
    for(const item of ordered.slice(0,200)){if(!item.event.geometry)continue;try{const geometry=sourceGeometry(item.event.geometry, item.event.track[0]?.longitude ?? 0) as Geometry;const id=`${item.event.source}:${item.event.id}`;const active=id===selected;const color=item.event.source==='NWS'?'#d9877c':'#83b9b3';const layer=L.geoJSON({type:'Feature',properties:{},geometry} as Feature<Geometry>,{style:{color,weight:active?4:2,fillOpacity:.1,dashArray:['EXPIRED','CANCELLED','REPORTED_CLOSED'].includes(item.state)?'5 5':undefined},pointToLayer:(_f,p)=>L.circleMarker(p,{radius:active?8:5,color,fillOpacity:.6})}).addTo(group);const label=document.createElement('span');label.textContent=`${item.event.title} · ${item.state} · observed/issued ${item.event.issuedAt}`;layer.bindTooltip(label).on('click',()=>select.current(id));if (active) {
          const anchor = item.event.track[0]?.longitude ?? 0;
          const track = reportedPositions(item.event.track, anchor);
          const bounds = layer.getBounds();
          if (track) {
            const line = L.geoJSON({ type: 'Feature', properties: {}, geometry: track as Geometry } as Feature<Geometry>, {
              interactive: false,
              style: { color, weight: 2, dashArray: '5 6', className: 'reported-position-track' },
            }).addTo(group);
            bounds.extend(line.getBounds());
          }
          if (bounds.isValid()) instance.fitBounds(bounds, { maxZoom: 8, padding: [35, 35], animate: false });
        }}catch{/* Source text remains accessible if geometry cannot render. */}}
    return()=>{group.remove();};
  },[items,selected]);
  return <><div ref={node} className="route-map timeline-map" aria-label="Available reported-event timeline map"/><p className="timestamp">At most 200 geometries are displayed. All filtered source records remain available below. This is retrieved history, not a live animation.</p></>;
}
