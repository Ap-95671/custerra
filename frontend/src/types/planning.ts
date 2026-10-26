import { z } from 'zod';
import { pointSchema, evidenceSchema } from './journey';
const time = z.string().datetime({ offset: true });
const source = z.object({ id: z.string(), attribution: z.string(), documentationUrl: z.string(), licensingUrl: z.string(), refreshPolicy: z.string() });
export const placeSchema = z.object({ id: z.string(), name: z.string(), region: z.string().nullable(), country: z.string().nullable(), location: pointSchema });
export const placesSchema = z.object({ candidates: z.array(placeSchema).max(5), source, retrievedAt: time });
export type Place = z.infer<typeof placeSchema>;
export const plannedRouteSchema = z.object({ geometry: z.string().max(200000), geometryPrecision: z.literal(6), distanceKm: z.number().positive().max(900), durationSeconds: z.number().nonnegative(),
  segmentCount: z.number().int().min(1).max(19999), retrievedAt: time, source, limitations: z.array(z.string()), assessmentError: z.string().nullable(),
  assessment: z.object({ modelVersion: z.string(), assessedAt: time, overallRisk: z.literal('UNKNOWN'), truncated: z.boolean(),
    matches: z.array(z.object({ event: evidenceSchema, distanceKm: z.number().nonnegative(), segmentIndex: z.number().int().nonnegative() })), sources: z.array(source), explanation: z.string() }).nullable(),
});
export type PlannedRoute = z.infer<typeof plannedRouteSchema>;
export function decodeRoute(encoded: string): [number, number][] {
  if (encoded.length > 200000) throw new Error('Route geometry is too large.');
  let index = 0, lat = 0, lon = 0;
  const points: [number, number][] = [];
  function number() {
    let value = 0, shift = 0;
    while (true) {
      if (index >= encoded.length || shift > 30) throw new Error('Invalid route geometry.');
      const digit = encoded.charCodeAt(index++) - 63;
      if (digit < 0 || digit > 63) throw new Error('Invalid route geometry.');
      value += (digit & 31) * 2 ** shift;
      if (digit < 32) return value % 2 ? -(Math.floor(value / 2) + 1) : value / 2;
      shift += 5;
    }
  }
  while (index < encoded.length) {
    lat += number(); lon += number();
    if (Math.abs(lat) > 90000000 || Math.abs(lon) > 180000000 || points.length >= 20000) throw new Error('Invalid route geometry.');
    points.push([lat / 1000000, lon / 1000000]);
  }
  if (points.length < 2) throw new Error('Invalid route geometry.');
  return points;
}
