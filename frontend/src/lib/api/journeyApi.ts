import { z } from 'zod';
import { csrfHeaders, SESSION_EXPIRED } from './session';
const base = (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
export async function api<T>(path: string, schema: z.ZodType<T>, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const timeout = AbortSignal.timeout(35000);
  let response: Response;
  const combinedSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
  try {
    response = await fetch(`${base}/api/v1/${path}`, { method, credentials: 'include', headers: { ...(['GET', 'HEAD'].includes(method) ? {} : await csrfHeaders(combinedSignal)), Accept: 'application/json', ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, body: body === undefined ? undefined : JSON.stringify(body), signal: combinedSignal, cache: 'no-store' });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new Error('Request interrupted or API unavailable. Reload saved journeys before retrying a save; it may have completed.');
  }
  if (response.status === 401) { window.dispatchEvent(new Event(SESSION_EXPIRED)); throw new Error('Your session has ended. Sign in again.'); }
  if (response.status === 403) throw new Error('Your session or security token has expired. Reload before retrying; the operation was not automatically repeated.');
  if (!response.ok) throw new Error(response.status === 429 ? 'Please wait a second before requesting this provider again.' : response.status === 422 ? 'No supported driving route was found. Choose road-accessible endpoints within 900 km; endpoint snapping is limited to 5 km.' : response.status === 400 ? 'Check coordinates, dates and limits. Routes must be 0.001–900 km; history windows must be 1–3,660 days, from 1900 onward and end in the past.' : response.status === 404 ? 'Saved item not found.' : response.status === 409 ? 'Activate the journey before checking it.' : `Request failed (${response.status}). Provider failures do not establish safe conditions.`);
  try { return schema.parse(response.status === 204 ? null : await response.json()); }
  catch { throw new Error('The API returned an unexpected response.'); }
}
