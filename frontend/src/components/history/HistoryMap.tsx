'use client';

import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import type { History } from '../../types/history';
import { mapConfig } from '../../lib/maps/mapConfig';

const identity = (event: History['events'][number]) => `${event.source}:${event.externalId}`;

export default function HistoryMap({ data, selected, onSelect }: {
  data: History;
  selected: string | null;
  onSelect: (key: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const positions = useRef(new Map<string, L.CircleMarker>());
  const [tileError, setTileError] = useState(false);

  useEffect(() => {
    if (!container.current) return;
    const clamp = (latitude: number) => Math.max(-85, Math.min(85, latitude));
    const { latitude, longitude, radiusKm } = data.query;
    const center = L.latLng(clamp(latitude), longitude);
    const instance = L.map(container.current, { minZoom: 2, preferCanvas: true }).setView(center, 4);
    map.current = instance;
    const current = positions.current;
    L.tileLayer(mapConfig.tileUrl, { attribution: mapConfig.attribution, maxZoom: 19 })
      .on('tileerror', () => setTileError(true)).addTo(instance);
    const area = L.circle(center, { radius: radiusKm * 1000, interactive: false, color: '#b2c8ba', weight: 1, fillOpacity: 0.04 }).addTo(instance);
    const bounds = area.getBounds();
    for (const event of data.events) {
      if (Math.abs(event.latitude) > 85) continue;
      const offset = ((event.longitude - longitude + 540) % 360) - 180;
      const position = L.latLng(event.latitude, longitude + offset);
      const marker = L.circleMarker(position, { color: '#ffd3a9', fillColor: '#d89060', fillOpacity: 0.78, radius: 5, weight: 1 }).addTo(instance);
      marker.on('click', () => onSelect(identity(event)));
      current.set(identity(event), marker);
      bounds.extend(position);
    }
    instance.fitBounds(bounds, { padding: [24, 24], maxZoom: 11, animate: false });
    const observer = new ResizeObserver(() => instance.invalidateSize());
    observer.observe(container.current);
    return () => {
      observer.disconnect();
      instance.remove();
      map.current = null;
      current.clear();
    };
  }, [data, onSelect]);

  useEffect(() => {
    const marker = selected ? positions.current.get(selected) : undefined;
    if (!marker) return;
    marker.setStyle({ color: '#fff', weight: 3 });
    marker.setRadius(8);
    marker.bringToFront();
    map.current?.panInside(marker.getLatLng(), { padding: [32, 32], animate: false });
    return () => {
      marker.setStyle({ color: '#ffd3a9', weight: 1 });
      marker.setRadius(5);
    };
  }, [selected]);

  return <>
    <div className="route-map historical-map" ref={container} aria-label="Map of earthquake catalog locations in the retrieved snapshot"/>
    <p className="timestamp">Circles mark catalog locations. The outline shows the search radius. High-latitude records outside the basemap remain in the event list.</p>
    {tileError && <p role="status">Some map tiles could not load. The catalog and measurements are still available.</p>}
  </>;
}
