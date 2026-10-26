"use client";
import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import { hazardLayers, eventLayer, hazardLabel, informationLabel } from '../../lib/maps/hazardLayers';
import { mapConfig } from '../../lib/maps/mapConfig';
import type { Query, HazardItem } from '../../types/hazard';
import { safeSourceUrl } from '../../types/hazard';
export default function DisasterMap({ query, items, visible, selected, onSelect, onPick }: {
  query: Query | null; items: HazardItem[]; visible: boolean; selected: string | null;
  onSelect: (id: string) => void; onPick: (latitude: number, longitude: number) => void;
}) {
  const node = useRef<HTMLDivElement>(null); const map = useRef<L.Map | null>(null);
  const handlers = useRef({ onPick, onSelect });
  const [tilesFailed, setTilesFailed] = useState(false);
  useEffect(() => { handlers.current = { onPick, onSelect }; }, [onPick, onSelect]);
  useEffect(() => {
    if (!node.current) return;
    const instance = L.map(node.current, { worldCopyJump: true, minZoom: 2 }).setView([24, 20], 2);
    map.current = instance;
    L.tileLayer(mapConfig.tileUrl, { maxZoom: 19, attribution: mapConfig.attribution })
      .on('tileerror', () => setTilesFailed(true)).addTo(instance);
    instance.on('click', (event: L.LeafletMouseEvent) => {
      const point = event.latlng.wrap();
      handlers.current.onPick(Math.max(-90, Math.min(90, point.lat)), point.lng);
    });
    const observer = new ResizeObserver(() => instance.invalidateSize()); observer.observe(node.current);
    return () => { observer.disconnect(); instance.remove(); map.current = null; };
  }, []);
  useEffect(() => {
    if (!query || !map.current) return;
    if (Math.abs(query.latitude) > 85) {
      map.current.setView([Math.sign(query.latitude) * 85, query.longitude], 3, { animate: false });
      return;
    }
    const circle = L.circle([query.latitude, query.longitude], { radius: query.radiusKm * 1000, color: '#23695e', weight: 1.5, fillOpacity: 0.04, interactive: false }).addTo(map.current);
    const center = L.circleMarker([query.latitude, query.longitude], { radius: 5, color: '#123b36', fillOpacity: 1, interactive: false }).addTo(map.current);
    map.current.fitBounds(circle.getBounds(), { maxZoom: 9, padding: [24, 24], animate: false });
    return () => { circle.remove(); center.remove(); };
  }, [query]);
  useEffect(() => {
    if (!map.current || !visible) return;
    const group = L.layerGroup().addTo(map.current);
    for (const item of items.slice(0, 300)) {
      const event = item.event;
      if (Math.abs(event.latitude) > 85) continue;
      const id = `${event.source}:${event.externalId}`;
      const layer = hazardLayers[eventLayer(event)];
      const marker = L.marker([event.latitude, event.longitude], { bubblingMouseEvents: false,
        title: `${layer.label}: ${event.title}`, keyboard: true,
        icon: L.divIcon({ className: `hazard-marker ${selected === id ? 'selected' : ''}`,
          html: `<span style="color:${layer.color}">${layer.symbol}</span>`, iconSize: [24, 24], iconAnchor: [12, 12] }),
      }).addTo(group);
      const content = document.createElement('div');
      const title = document.createElement('strong'); title.textContent = event.title; content.append(title);
      const text = document.createElement('p'); text.textContent = `${hazardLabel(event)} · ${informationLabel(event.informationType)} · Severity: ${event.severity} · Confidence: ${event.confidence ?? 'UNKNOWN'} · ${item.distanceKm.toFixed(1)} km away · ${event.startedAt} · Source: ${event.source}`; content.append(text);
      const href = safeSourceUrl(event.sourceUrl);
      if (href) { const link = document.createElement('a'); link.href = href; link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = 'View source evidence ↗'; content.append(link); }
      marker.bindPopup(content).on('click', () => handlers.current.onSelect(id));
      if (selected === id) marker.openPopup();
    }
    return () => { group.remove(); };
  }, [items, visible, selected]);
  return <>{items.length > 300 && <div className="marker-limit" role="status">Map shows the nearest 300 enabled events. All retrieved evidence remains listed below.</div>}<div ref={node} className="map-canvas" aria-label="Interactive world map. Click to choose coordinates, or use the coordinate form." />{query && Math.abs(query.latitude) > 85 && <div className="polar-note" role="status">Polar coordinates are outside the basemap extent. Results below still use the selected coordinates.</div>}{tilesFailed && <div className="tile-error" role="status">Some map tiles could not load. Coordinate search and event details remain available.</div>}</>;
}
