"use client";
import { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import type { Geometry,Feature } from 'geojson';
import {sourceGeometry} from '../../lib/maps/sourceGeometry';
import type {RouteExposure} from '../../types/intelligence';
import { mapConfig } from '../../lib/maps/mapConfig';
import { decodeRoute, type PlannedRoute } from '../../types/planning';
export default function RouteMap({ route, areaExposure, selectedSegment, onSelectSegment }: { route: PlannedRoute; areaExposure?:RouteExposure|null; selectedSegment?: number|null; onSelectSegment?: (index:number)=>void }) {
  const node = useRef<HTMLDivElement>(null); const [error, setError] = useState('');
  const mapRef=useRef<L.Map|null>(null);const selectRef=useRef(onSelectSegment);
  useEffect(()=>{selectRef.current=onSelectSegment;},[onSelectSegment]);
  const points = useMemo(() => { try { return decodeRoute(route.geometry); } catch { return null; } }, [route.geometry]);
  useEffect(() => {
    if (!node.current || !points) return;
    const map = L.map(node.current, { minZoom: 2 });
    mapRef.current=map;
    const observer = new ResizeObserver(() => map.invalidateSize()); observer.observe(node.current);
    L.tileLayer(mapConfig.tileUrl, { attribution: mapConfig.attribution, maxZoom: 19 }).on('tileerror', () => setError('Some map tiles could not load. Route evidence remains available below.')).addTo(map);

      const line = L.polyline(points, { color: '#80a99d', weight: 4, className: 'planned-route-line' }).addTo(map);
      map.fitBounds(line.getBounds(), { padding: [25, 25], maxZoom: 14, animate: false });
      const path = line.getElement();
      path?.setAttribute('pathLength', '1');
      path?.setAttribute('data-draw', '');
      L.circleMarker(points[0], { radius: 7, color: '#123b36' }).addTo(map).bindTooltip('From · snapped road position');
      L.circleMarker(points[points.length - 1], { radius: 7, color: '#123b36' }).addTo(map).bindTooltip('To · snapped road position');
      const segments = new Set(route.assessment?.matches.map(match => match.segmentIndex) ?? []);
      for (const index of segments) if (index < points.length - 1) L.polyline([points[index], points[index + 1]], { color: '#d5956d', weight: 7 }).addTo(map).bindTooltip(`Evidence near segment ${index + 1}`).on('click',()=>selectRef.current?.(index));
      for (const match of route.assessment?.matches ?? []) {
        const text = document.createElement('div'); text.textContent = `${match.event.title} · ${match.distanceKm.toFixed(1)} km from segment ${match.segmentIndex + 1}`;
        L.circleMarker([match.event.latitude, match.event.longitude], { radius: 6, color: '#d5956d' }).addTo(map).bindPopup(text).on('click',()=>selectRef.current?.(match.segmentIndex));
      }

    return () => { observer.disconnect(); map.remove(); mapRef.current=null; };
  }, [route, points]);
  useEffect(()=>{
    if(!mapRef.current || !points || selectedSegment==null || selectedSegment<0 || selectedSegment>=points.length-1)return;
    const line=L.polyline([points[selectedSegment],points[selectedSegment+1]],{color:'#d9ece4',weight:9,className:'selected-route-segment'}).addTo(mapRef.current);
    mapRef.current.panInside(points[selectedSegment],{padding:[35,35],animate:false});
    return()=>{line.remove();};
  },[selectedSegment,points]);
  useEffect(()=>{if(!mapRef.current||!areaExposure)return;const group=L.layerGroup().addTo(mapRef.current);for(const item of areaExposure.items){if(!item.event.geometry)continue;try{L.geoJSON({type:'Feature',properties:{},geometry:sourceGeometry(item.event.geometry,points?.[0][1]??0) as Geometry} as Feature<Geometry>,{style:{color:item.event.source==='NWS'?'#d9877c':'#83b9b3',weight:2,fillOpacity:.1}}).addTo(group);}catch{/* Source record remains listed. */}}return()=>{group.remove();};},[areaExposure,points]);
  return <><div ref={node} className="route-map" aria-label="Retrieved driving route and nearby earthquake evidence" />{!points && <p role="alert">The route geometry could not be displayed. Evidence remains listed below.</p>}{error && <p role="status">{error}</p>}</>;
}
