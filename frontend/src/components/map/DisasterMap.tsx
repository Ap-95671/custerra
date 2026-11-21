"use client";
import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import { hazardLayers, eventLayer, hazardLabel, informationLabel } from '../../lib/maps/hazardLayers';
import { mapConfig } from '../../lib/maps/mapConfig';
import type { Query, HazardItem } from '../../types/hazard';
import { safeSourceUrl } from '../../types/hazard';
import type { IntelligenceItem } from '../../types/intelligence';
import { reportedPositions, sourceGeometry } from '../../lib/maps/sourceGeometry';
import type { Geometry,Feature } from 'geojson';
import { groupMarkers } from '../../lib/maps/clusters';
import { useLocationAssessment } from '../../hooks/useLocationAssessment';
export default function DisasterMap({ query, items, intelligenceItems, visible, selected, onSelect, onPick }: {
  query: Query | null; items: HazardItem[]; intelligenceItems: IntelligenceItem[]; visible: boolean; selected: string | null;
  onSelect: (id: string) => void; onPick: (latitude: number, longitude: number) => void;
}) {
  const {mapViewport,setMapViewport}=useLocationAssessment();
  const initialViewport = useRef(mapViewport);
  const initialQuery = useRef(query);
  const previouslyFocused = useRef(selected && mapViewport ? selected : null);
  const node = useRef<HTMLDivElement>(null); const map = useRef<L.Map | null>(null);
  const handlers = useRef({ onPick, onSelect });
  const [tilesFailed, setTilesFailed] = useState(false);
  useEffect(() => { handlers.current = { onPick, onSelect }; }, [onPick, onSelect]);
  useEffect(() => {
    if (!node.current) return;
    const saved=initialViewport.current;
    const instance = L.map(node.current, { worldCopyJump: true, minZoom: 2 }).setView(saved?[saved.latitude,saved.longitude]:[24,20],saved?.zoom??2);
    instance.on('moveend',()=>{const center=instance.getCenter();setMapViewport({latitude:center.lat,longitude:center.lng,zoom:instance.getZoom()});});
    map.current = instance;
    L.tileLayer(mapConfig.tileUrl, { maxZoom: 19, attribution: mapConfig.attribution })
      .on('tileerror', () => setTilesFailed(true)).addTo(instance);
    instance.on('click', (event: L.LeafletMouseEvent) => {
      const point = event.latlng.wrap();
      handlers.current.onPick(Math.max(-90, Math.min(90, point.lat)), point.lng);
    });
    const observer = new ResizeObserver(() => instance.invalidateSize()); observer.observe(node.current);
    return () => { observer.disconnect(); instance.remove(); map.current = null; };
  }, [setMapViewport]);
  useEffect(() => {
    if (!query || !map.current) return;
    const restoreCamera = initialViewport.current !== null && initialQuery.current === query;
    if (Math.abs(query.latitude) > 85) {
      if (!restoreCamera) map.current.setView([Math.sign(query.latitude) * 85, query.longitude], 3, { animate: false });
      return;
    }
    const circle = L.circle([query.latitude, query.longitude], { radius: query.radiusKm * 1000, color: '#b2c8ba', weight: 1.5, fillOpacity: 0.04, interactive: false }).addTo(map.current);
    const center = L.circleMarker([query.latitude, query.longitude], { radius: 5, color: '#d98b63', fillOpacity: 1, interactive: false }).addTo(map.current);
    if (!restoreCamera) map.current.fitBounds(circle.getBounds(), { maxZoom: 9, paddingTopLeft: map.current.getSize().x>600?[290,30]:[24,24], paddingBottomRight:[35,110], animate: false });
    return () => { circle.remove(); center.remove(); };
  }, [query]);
  useEffect(() => {
    if (!map.current || !visible) return;
    const instance=map.current;const group = L.layerGroup().addTo(instance);
    function draw() {
      group.clearLayers();
      const groups=groupMarkers(items,item=>instance.latLngToLayerPoint([item.event.latitude,item.event.longitude]),selected);
      for (const cluster of groups.slice(0,300)) {
        const item=cluster.items[0];const event=item.event;
        if(cluster.items.length>1){
          const center=L.latLngBounds(cluster.items.map(value=>[value.event.latitude,value.event.longitude] as [number,number])).getCenter();
          L.marker(center,{bubblingMouseEvents:false,keyboard:true,title:`${cluster.items.length} observations - zoom to inspect`,icon:L.divIcon({className:'hazard-cluster',html:String(cluster.items.length),iconSize:[34,34],iconAnchor:[17,17]})}).addTo(group).on('click',()=>instance.setView(center,Math.min(instance.getZoom()+2,19),{animate:!window.matchMedia('(prefers-reduced-motion: reduce)').matches}));
          continue;
        }
        const id=`${event.source}:${event.externalId}`;const layer=hazardLayers[eventLayer(event)];
        const marker=L.marker([event.latitude,event.longitude],{bubblingMouseEvents:false,title:`${layer.label}: ${event.title}`,keyboard:true,icon:L.divIcon({className:`hazard-marker ${selected===id?'selected':''}`,html:`<span style="color:${layer.color}">${layer.symbol}</span>`,iconSize:[26,26],iconAnchor:[13,13]})}).addTo(group);
        const content=document.createElement('div');const title=document.createElement('strong');title.textContent=event.title;content.append(title);
        const text=document.createElement('p');text.textContent=`${hazardLabel(event)} · ${informationLabel(event.informationType)} · Severity: ${event.severity} · Confidence: ${event.confidence??'UNKNOWN'} · ${item.distanceKm.toFixed(1)} km away · ${event.startedAt} · Source: ${event.source}`;content.append(text);
        const href=safeSourceUrl(event.sourceUrl);if(href){const link=document.createElement('a');link.href=href;link.target='_blank';link.rel='noopener noreferrer';link.textContent='View source evidence ↗';content.append(link);}
        marker.bindPopup(content, { autoPan: false }).on('click',()=>handlers.current.onSelect(id));if(selected===id)marker.openPopup();
      }
    }
    const chosen=items.find(item=>`${item.event.source}:${item.event.externalId}`===selected);
    if (chosen && previouslyFocused.current !== selected && Math.abs(chosen.event.latitude) <= 85) {
      previouslyFocused.current = selected;
      instance.panInside([chosen.event.latitude, chosen.event.longitude], {
        paddingTopLeft: instance.getSize().x > 600 ? [300, 80] : [40, 80],
        paddingBottomRight: [50, 100],
        animate: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
      });
    }
    if (!selected) previouslyFocused.current = null;
    draw();instance.on('zoomend',draw);
    return () => { instance.off('zoomend',draw);group.remove(); };
  }, [items, visible, selected]);
  useEffect(()=>{
    if(!map.current)return;const instance=map.current;const group=L.layerGroup().addTo(instance);
    const chosen=intelligenceItems.find(item=>`INTELLIGENCE:${item.event.source}:${item.event.id}`===selected);
    const candidates=chosen?[chosen,...intelligenceItems.filter(i=>i!==chosen)]:intelligenceItems;
    for(const item of candidates.slice(0,100)){
      if(!item.event.geometry)continue;
      try{
        const geometry=sourceGeometry(item.event.geometry,query?.longitude??0) as Geometry;
        const selectedItem=`INTELLIGENCE:${item.event.source}:${item.event.id}`===selected;
        const color=item.event.source==='NWS'?(item.event.severity==='Extreme'||item.event.severity==='Severe'?'#d9877c':'#c5b57f'):'#83b9b3';
        const feature=L.geoJSON({type:'Feature',properties:{},geometry} as Feature<Geometry>,{style:{color,weight:selectedItem?4:2,fillOpacity:selectedItem ? .22 : .08},pointToLayer:(_feature,latlng)=>L.circleMarker(latlng,{radius:selectedItem?8:5,color,fillOpacity:.65})}).addTo(group);
        const text=document.createElement('span');text.textContent=`${item.event.source} · ${item.event.title} · ${item.state}`;
        feature.bindTooltip(text).on('click',()=>handlers.current.onSelect(`INTELLIGENCE:${item.event.source}:${item.event.id}`));
        if (selectedItem) {
          const track = reportedPositions(item.event.track, query?.longitude ?? 0);
          if (track) {
            L.geoJSON({ type: 'Feature', properties: {}, geometry: track as Geometry } as Feature<Geometry>, {
              interactive: false,
              style: { color, weight: 2, dashArray: '5 6', className: 'reported-position-track' },
            }).addTo(group);
          }
        }
        if (selectedItem && previouslyFocused.current !== selected) {
          const bounds = feature.getBounds();
          if (bounds.isValid()) {
            previouslyFocused.current = selected;
            instance.fitBounds(bounds, {
              maxZoom: 9,
              paddingTopLeft: instance.getSize().x > 600 ? [300, 35] : [35, 35],
              paddingBottomRight: [35, 100],
              animate: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
            });
          }
        }
      }catch{/* Invalid/unsupported display geometry remains available as source text. */}
    }
    return()=>{group.remove();};
  },[intelligenceItems,selected,query]);
  return <>{items.length > 300 && <div className="marker-limit" role="status">Dense observations are grouped by screen position. Up to 300 markers or groups are drawn. All retrieved evidence remains listed below.</div>}<div ref={node} className="map-canvas" aria-label="Interactive world map. Click to choose coordinates, or use the coordinate form." />{query && Math.abs(query.latitude) > 85 && <div className="polar-note" role="status">Polar coordinates are outside the basemap extent. Results below still use the selected coordinates.</div>}{tilesFailed && <div className="tile-error" role="status">Some map tiles could not load. Coordinate search and event details remain available.</div>}</>;
}
