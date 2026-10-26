"use client";
import { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import { mapConfig } from '../../lib/maps/mapConfig';
import { decodeRoute, type PlannedRoute } from '../../types/planning';
export default function RouteMap({ route }: { route: PlannedRoute }) {
  const node = useRef<HTMLDivElement>(null); const [error, setError] = useState('');
  const points = useMemo(() => { try { return decodeRoute(route.geometry); } catch { return null; } }, [route.geometry]);
  useEffect(() => {
    if (!node.current || !points) return;
    const map = L.map(node.current, { minZoom: 2 });
    const observer = new ResizeObserver(() => map.invalidateSize()); observer.observe(node.current);
    L.tileLayer(mapConfig.tileUrl, { attribution: mapConfig.attribution, maxZoom: 19 }).on('tileerror', () => setError('Some map tiles could not load. Route evidence remains available below.')).addTo(map);

      const line = L.polyline(points, { color: '#23695e', weight: 4 }).addTo(map); map.fitBounds(line.getBounds(), { padding: [25, 25], maxZoom: 14 });
      L.circleMarker(points[0], { radius: 7, color: '#123b36' }).addTo(map).bindTooltip('From · snapped road position');
      L.circleMarker(points[points.length - 1], { radius: 7, color: '#123b36' }).addTo(map).bindTooltip('To · snapped road position');
      const segments = new Set(route.assessment?.matches.map(match => match.segmentIndex) ?? []);
      for (const index of segments) if (index < points.length - 1) L.polyline([points[index], points[index + 1]], { color: '#b14520', weight: 7 }).addTo(map).bindTooltip(`Evidence near segment ${index + 1}`);
      for (const match of route.assessment?.matches ?? []) {
        const text = document.createElement('div'); text.textContent = `${match.event.title} · ${match.distanceKm.toFixed(1)} km from segment ${match.segmentIndex + 1}`;
        L.circleMarker([match.event.latitude, match.event.longitude], { radius: 6, color: '#935435' }).addTo(map).bindPopup(text);
      }

    return () => { observer.disconnect(); map.remove(); };
  }, [route, points]);
  return <><div ref={node} className="route-map" aria-label="Retrieved driving route and nearby earthquake evidence" />{!points && <p role="alert">The route geometry could not be displayed. Evidence remains listed below.</p>}{error && <p role="status">{error}</p>}</>;
}
