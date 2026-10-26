import { afterEach, expect, test, vi } from 'vitest';
import { authenticate, csrfHeaders, readSession } from '../src/lib/api/session';
import { api } from '../src/lib/api/journeyApi';
import { z } from 'zod';
afterEach(() => vi.unstubAllGlobals());
test('registration and login each obtain a fresh CSRF token and send cookies', async () => {
  let tokens = 0;
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    expect(options?.credentials).toBe('include');
    if (url.endsWith('/csrf')) return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: `csrf-${++tokens}` }));
    return new Response(null, { status: url.endsWith('/register') ? 201 : 204 });
  });
  vi.stubGlobal('fetch', fetcher);
  await authenticate('test_user', 'long-fixture-password', true);
  const writes = fetcher.mock.calls.filter(([, options]) => options?.method === 'POST');
  expect(writes).toHaveLength(2);
  expect(writes[0][1]?.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-1' });
  expect(writes[1][1]?.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-2', 'Content-Type': 'application/x-www-form-urlencoded' });
});
test('missing or malformed session and CSRF responses do not imply login', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 401 })));
  expect(await readSession()).toBeNull();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}')));
  await expect(readSession()).rejects.toThrow();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}')));
  await expect(csrfHeaders()).rejects.toThrow();
});
test('expired sessions notify the UI once and never retry a mutation automatically', async () => {
  const dispatchEvent = vi.fn(); vi.stubGlobal('window', { dispatchEvent });
  const fetcher = vi.fn((url: string) => Promise.resolve(url.endsWith('/csrf') ? new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'fixture' })) : new Response('', { status: 401 })));
  vi.stubGlobal('fetch', fetcher);
  await expect(api('journeys', z.array(z.unknown()), 'POST', {})).rejects.toThrow('session has ended');
  expect(dispatchEvent).toHaveBeenCalledOnce();
  expect(fetcher).toHaveBeenCalledTimes(2);
});
