import { afterEach, expect, test, vi } from 'vitest';
import { getNearby, getWeather } from '../src/lib/api/apiClient';
import { querySchema, safeSourceUrl } from '../src/types/hazard';
const query = { latitude: 0, longitude: 0, radiusKm: 100, days: 7 };
afterEach(() => vi.unstubAllGlobals());
test('query rejects NaN, bad coordinates and unbounded searches', () => {
  for (const invalid of [{ latitude: NaN }, { longitude: 181 }, { radiusKm: 0 }, { days: 31 }]) expect(querySchema.safeParse({ ...query, ...invalid }).success).toBe(false);
});
test('source links reject script URLs and embedded credentials', () => {
  expect(safeSourceUrl('javascript:alert(1)')).toBeUndefined();
  expect(safeSourceUrl('https://user:pass@example.test')).toBeUndefined();
  expect(safeSourceUrl('https://earthquake.usgs.gov/')).toBe('https://earthquake.usgs.gov/');
});
test('builds one bounded request with abort support', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ fetchedAt: '2026-01-01T00:00:00Z', truncated: false, coverage: 'test', sources: [], items: [] })));
  vi.stubGlobal('fetch', fetcher);
  const result = await getNearby(query, new AbortController().signal);
  expect(result.items).toEqual([]);
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(fetcher.mock.calls[0][0]).toContain('latitude=0&longitude=0&radiusKm=100&days=7');
  expect(fetcher.mock.calls[0][1].signal).toBeInstanceOf(AbortSignal);
});
test('malformed JSON and wrong schemas do not become successful empty data', async () => {
  for (const value of ['<html>error</html>', '{}']) {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(value)));
    await expect(getNearby(query, new AbortController().signal)).rejects.toThrow('unexpected response');
  }
});
test('provider failure is explicit', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('private details', { status: 502 })));
  await expect(getNearby(query, new AbortController().signal)).rejects.toThrow('provider is unavailable');
});
test('unreachable API reports connectivity rather than no observations', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed')));
  await expect(getNearby(query, new AbortController().signal)).rejects.toThrow('Cannot reach');
});

test('weather uses coordinates only and identifies its own provider errors', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response('', { status: 504 }));
  vi.stubGlobal('fetch', fetcher);
  await expect(getWeather(query, new AbortController().signal)).rejects.toThrow('weather provider timed out');
  expect(fetcher.mock.calls[0][0]).toContain('/weather/location?latitude=0&longitude=0');
  expect(fetcher.mock.calls[0][0]).not.toContain('radiusKm');
});
