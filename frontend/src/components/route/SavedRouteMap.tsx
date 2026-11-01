"use client";
import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import type { Journey } from '../../types/journey';
import { mapConfig } from '../../lib/maps/mapConfig';

export default function SavedRouteMap({ journey }: { journey: Journey }) {
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
    const p=journey.progress;
    if(p?.state==='MATCHED' && now-Date.parse(p.observedAt)<=300000)L.polyline(points.slice(p.segmentIndex),{color:'#093f59',weight:6}).addTo(map);
    return ()=>{observer.disconnect();map.remove();};
  },[journey,now]);
  return <><div ref={node} className="route-map" aria-label="Saved journey route"/><p>Saved geometry is shown in gray; a recent matched remaining route is shown in navy. Route details and timestamps remain available if map tiles fail.</p></>;
}
