"use client";
import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import type { Journey } from '../../types/journey';
import type {RouteExposure} from '../../types/intelligence';
import type {Feature,Geometry} from 'geojson';
import {sourceGeometry} from '../../lib/maps/sourceGeometry';
import { mapConfig } from '../../lib/maps/mapConfig';

export default function SavedRouteMap({ journey,areaExposure,selectedSegment }: { journey: Journey;areaExposure?:RouteExposure|null;selectedSegment?:number|null }) {
  const [now,setNow]=useState(()=>Date.now());
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),30000);return ()=>clearInterval(timer);},[]);
  const node=useRef<HTMLDivElement>(null);
  useEffect(()=>{
    if(!node.current)return;
    const map=L.map(node.current,{minZoom:2});
    const observer=new ResizeObserver(()=>map.invalidateSize());observer.observe(node.current);
    L.tileLayer(mapConfig.tileUrl,{attribution:mapConfig.attribution,maxZoom:19}).addTo(map);
    // Unwrap date-line crossings so Leaflet draws adjacent spherical endpoints together.
    let previous=journey.route.points[0].longitude;
    const points=journey.route.points.map(p=>{let longitude=p.longitude;while(longitude-previous>180)longitude-=360;while(longitude-previous< -180)longitude+=360;previous=longitude;return [p.latitude,longitude] as [number,number];});
    const line=L.polyline(points,{color:'#71828b',weight:4}).addTo(map);
    map.fitBounds(line.getBounds(),{padding:[20,20],maxZoom:14});
    for(const item of areaExposure?.items??[]){if(!item.event.geometry)continue;try{L.geoJSON({type:'Feature',properties:{},geometry:sourceGeometry(item.event.geometry,points[0][1]) as Geometry} as Feature<Geometry>,{style:{color:item.event.source==='NWS'?'#d9877c':'#83b9b3',weight:2,fillOpacity:.1}}).addTo(map);}catch{/* Source records remain available as text. */}}
    if(selectedSegment!=null&&selectedSegment>=0&&selectedSegment<points.length-1){const focus=L.polyline(points.slice(selectedSegment,selectedSegment+2),{color:'#d9877c',weight:8,className:'selected-route-segment'}).addTo(map);map.fitBounds(focus.getBounds(),{padding:[35,35],maxZoom:13});}
    const p=journey.progress;
    if(p?.state==='MATCHED' && now-Date.parse(p.observedAt)<=300000)L.polyline(points.slice(p.segmentIndex),{color:'#8eb8ad',weight:6,className:'remaining-route-line'}).addTo(map);
    if(p){const label=document.createElement('span');label.textContent=`${now-Date.parse(p.observedAt)>300000?'STALE':p.state} position · ±${p.accuracyMeters.toFixed(0)} m`;L.circleMarker([p.position.latitude,p.position.longitude],{radius:6,color:'#db9c75',fillOpacity:.8}).addTo(map).bindTooltip(label);}
    return ()=>{observer.disconnect();map.remove();};
  },[journey,now,areaExposure,selectedSegment]);
  return <><div ref={node} className="route-map" aria-label="Saved journey route"/><p>Saved geometry is shown in gray; a recent matched remaining route is shown in teal. Route details and timestamps remain available if map tiles fail.</p></>;
}
