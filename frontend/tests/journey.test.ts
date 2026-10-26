import { afterEach, expect, test, vi } from 'vitest';
import { z } from 'zod';
import { parsePoints, routeInputSchema } from '../src/types/journey';
import { api } from '../src/lib/api/journeyApi';
afterEach(() => vi.unstubAllGlobals());
test('waypoint parser preserves order and rejects empty, extra and invalid coordinates', () => {
  expect(parsePoints('0,179\n0,-179')).toEqual([{ latitude: 0, longitude: 179 }, { latitude: 0, longitude: -179 }]);
  for (const input of ['', ',1\n0,1', '1,2,3', 'NaN,1', '91,0']) expect(() => parsePoints(input)).toThrow();
  expect(routeInputSchema.safeParse({ points: parsePoints('0,0'), corridorKm: 25, days: 7, minMagnitude: 4 }).success).toBe(false);
});
test('write client sends JSON and validates responses, including no-content deletion', async () => {
  const writer = vi.fn().mockResolvedValueOnce(new Response('{"id":"stored"}', { status: 201 })).mockResolvedValueOnce(new Response(null, { status: 204 }));
  const fetcher = vi.fn((url: string, options?: RequestInit) => url.endsWith('/auth/csrf') ? Promise.resolve(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'fixture' }))) : writer(url, options));
  vi.stubGlobal('fetch', fetcher);
  expect(await api('journeys', z.object({ id: z.string() }), 'POST', { name: 'Test' })).toEqual({ id: 'stored' });
  expect(writer.mock.calls[0][1].body).toBe('{"name":"Test"}');
  expect(writer.mock.calls[0][1].method).toBe('POST');
  expect(await api('journeys/stored', z.null(), 'DELETE')).toBe(null);
});
test('write client never treats invalid responses or provider failures as empty success', async () => {
  for (const response of [new Response('{}'), new Response('', { status: 502 }), new Response('', { status: 409 })]) {
    vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(url.endsWith('/auth/csrf') ? new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'fixture' })) : response)));
    await expect(api('routes/assessment', z.object({ modelVersion: z.string() }), 'POST', {})).rejects.toThrow();
  }
});
