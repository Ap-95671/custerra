import { z } from 'zod';
import { coordinatesSchema, weatherSchema } from '../../types/weather';
import { nearbySchema, riskSchema, querySchema, type Query } from '../../types/hazard';
const base = (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
export async function request<T>(path: string, query: Query | { latitude: number; longitude: number }, schema: z.ZodType<T>, signal: AbortSignal): Promise<T> {
  if ('radiusKm' in query) querySchema.parse(query); else coordinatesSchema.parse(query);
  const provider = path.startsWith('weather/') ? 'weather' : 'hazard';
  const params = new URLSearchParams(Object.entries(query).map(([key, value]) => [key, String(value)]));
  const timeout = AbortSignal.timeout(35000);
  let response: Response;
  try { response = await fetch(`${base}/api/v1/${path}?${params}`, { signal: AbortSignal.any([signal, timeout]), headers: { Accept: 'application/json' }, cache: 'no-store' }); }
  catch (error) {
    if (signal.aborted) throw error;
    throw new Error(timeout.aborted ? 'The request timed out. Please try again.' : 'Cannot reach Custerra API. Check the backend connection and try again.');
  }
  if (!response.ok) throw new Error(response.status === 504 ? `The ${provider} provider timed out. Please try again.` : response.status === 502 ? `The ${provider} provider is unavailable. Please try again.` : `The API could not complete this request (${response.status}).`);
  try { return schema.parse(await response.json()); }
  catch { throw new Error('The API returned an unexpected response. Please try again.'); }
}
export const getNearby = (query: Query, signal: AbortSignal) => request('hazards/nearby', query, nearbySchema, signal);
export const getRisk = (query: Query, signal: AbortSignal) => request('risk/multi-hazard', query, riskSchema, signal);

export const getWeather = (query: Query, signal: AbortSignal) => request('weather/location', { latitude: query.latitude, longitude: query.longitude }, weatherSchema, signal);
