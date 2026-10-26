import { z } from 'zod';
export const apiBase = (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
export const identitySchema = z.object({ id: z.string().uuid(), username: z.string() });
export type Identity = z.infer<typeof identitySchema>;
export const SESSION_EXPIRED = 'custerra-session-expired';
export async function csrfHeaders(signal?: AbortSignal): Promise<Record<string, string>> {
  const response = await fetch(`${apiBase}/api/v1/auth/csrf`, { credentials: 'include', cache: 'no-store', signal: signal ?? AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error('Could not prepare a secure request. Please try again.');
  const token = z.object({ headerName: z.literal('X-CSRF-TOKEN'), token: z.string().min(1) }).parse(await response.json());
  return { [token.headerName]: token.token };
}
export async function readSession(signal?: AbortSignal): Promise<Identity | null> {
  const response = await fetch(`${apiBase}/api/v1/auth/me`, { credentials: 'include', cache: 'no-store', signal: signal ?? AbortSignal.timeout(15000) });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error('Account service unavailable. Retry when connected.');
  return identitySchema.parse(await response.json());
}
export async function authenticate(username: string, password: string, register: boolean) {
  if (register) {
    const response = await fetch(`${apiBase}/api/v1/auth/register`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', ...await csrfHeaders() }, body: JSON.stringify({ username, password }), signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw new Error(response.status === 409 ? 'That username is unavailable.' : response.status === 429 ? 'Too many attempts. Try again in ten minutes.' : 'Account creation failed. Use 3–40 letters, digits or underscores and a password of 12–72 UTF-8 bytes.');
  }
  const response = await fetch(`${apiBase}/api/v1/auth/login`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/x-www-form-urlencoded', ...await csrfHeaders() }, body: new URLSearchParams({ username, password }), signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(response.status === 429 ? 'Too many attempts. Try again in ten minutes.' : response.status === 401 ? 'Invalid username or password.' : 'Sign-in failed. Please try again.');
}
export async function endSession() {
  const response = await fetch(`${apiBase}/api/v1/auth/logout`, { method: 'POST', credentials: 'include', headers: await csrfHeaders(), signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error('Sign-out failed. Please retry.');
}
