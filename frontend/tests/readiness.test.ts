import { afterEach, expect, test, vi } from 'vitest';
import { webReadiness } from '../src/lib/server/webReadiness';
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); });
test('web readiness requires configured API/database readiness and hides dependency details', async () => {
  vi.stubEnv('BACKEND_INTERNAL_URL', 'http://private-api:8080');
  const fetcher = vi.fn().mockResolvedValue(Response.json({ status: 'UP' }));
  vi.stubGlobal('fetch', fetcher);
  expect((await webReadiness()).status).toBe(200);
  fetcher.mockResolvedValue(Response.json({ status: 'DOWN', components: { db: 'private details' } }, { status: 503 }));
  const down = await webReadiness(); expect(down.status).toBe(503);
  expect(await down.json()).toEqual({ status: 'DOWN' });
  expect(down.headers.get('Cache-Control')).toBe('no-store');
  fetcher.mockRejectedValue(new Error('private connection details'));
  expect((await webReadiness()).status).toBe(503);
  vi.stubEnv('BACKEND_INTERNAL_URL', ''); vi.stubEnv('BACKEND_HOST', '');
  expect((await webReadiness()).status).toBe(503);
});
