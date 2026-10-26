import { authFixture } from './authFixture';
import { test, expect, type Page } from '@playwright/test';
test.beforeEach(async ({ page }) => { await page.route('**/api/v1/auth/**', route => authFixture(route) || route.fallback()); });
const time = '2026-10-09T12:00:00Z';
const source = { id: 'OSRM', attribution: 'OSRM / OpenStreetMap contributors', documentationUrl: 'https://project-osrm.org/docs/v5.24.0/api/', licensingUrl: 'https://routing.openstreetmap.de/about.html', refreshPolicy: 'Test fixture' };
const planned = { geometry: '???_c`|@', geometryPrecision: 6, distanceKm: 112, durationSeconds: 7200, segmentCount: 1, source, retrievedAt: time, assessmentError: null, limitations: ['Test fixture: no safe travel conclusion.'], assessment: { modelVersion: 'route-proximity-v1', assessedAt: time, overallRisk: 'UNKNOWN', truncated: false, matches: [], sources: [], explanation: 'No currently retrieved monitored earthquakes intersect the route within the configured corridor and magnitude filter.' } };
async function setup(page: Page, failure = false) {
  await page.route('https://tile.openstreetmap.org/**', route => route.abort());
  await page.route('**/api/v1/**', route => {
    const auth = authFixture(route); if (auth) return auth;
    const path = new URL(route.request().url());
    if (path.pathname.endsWith('/monitoring')) return route.fulfill({ json: { schedulerEnabled: false, intervalMs: 300000 } });
    if (path.pathname.endsWith('/search')) {
      const from = path.searchParams.get('q') === 'Delhi';
      return route.fulfill({ json: { candidates: [{ id: from ? '1' : '2', name: from ? 'Delhi' : 'Manali', region: null, country: 'India', location: { latitude: 0, longitude: from ? 0 : 1 } }], source: { ...source, id: 'OPEN_METEO_GEOCODING', attribution: 'Open-Meteo / GeoNames' }, retrievedAt: time } });
    }
    if (path.pathname.endsWith('/plan')) return route.fulfill({ status: failure ? 502 : 200, json: failure ? {} : planned });
    return route.fulfill({ json: [] });
  });
}
async function choose(page: Page) {
  await page.getByLabel('From', { exact: true }).fill('Delhi');
  await page.getByRole('button', { name: 'Search from', exact: true }).click();
  await page.getByRole('radio', { name: /Delhi, India/ }).check();
  await page.getByLabel('To', { exact: true }).fill('Manali');
  await page.getByRole('button', { name: 'Search to', exact: true }).click();
  await page.getByRole('radio', { name: /Manali, India/ }).check();
}
test('origin and destination selections produce a road route, evidence and provider attribution', async ({ page }) => {
  await setup(page); await page.goto('/routes'); await choose(page);
  const request = page.waitForRequest(r => r.url().includes('/routes/plan'));
  await page.getByRole('button', { name: 'Analyze route', exact: true }).click();
  expect((await request).postDataJSON()).toMatchObject({ origin: { latitude: 0, longitude: 0 }, destination: { latitude: 0, longitude: 1 }, mode: 'DRIVING' });
  await expect(page.getByRole('heading', { name: '112.0 km driving route' })).toBeVisible();
  await expect(page.getByText('Route exposure · UNKNOWN')).toBeVisible();
  await expect(page.locator('.route-map .leaflet-interactive')).toHaveCount(3);
  await expect(page.getByRole('link', { name: 'Fix the map' })).toBeVisible();
  await page.getByLabel('From', { exact: true }).fill('Changed place');
  await expect(page.getByRole('region', { name: 'Driving route result' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Analyze route', exact: true })).toBeDisabled();
});
test('routing failure has a retryable message and does not produce a fabricated route', async ({ page }) => {
  await setup(page, true); await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/routes'); await choose(page);
  await page.getByRole('button', { name: 'Analyze route', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Driving route planner' }).getByRole('alert')).toContainText('Request failed (502)');
  await expect(page.getByRole('region', { name: 'Driving route result' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Analyze route', exact: true })).toBeEnabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('hazard failure retains the driving route with explicitly unknown exposure', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/routes/plan', route => route.fulfill({ json: { ...planned, assessment: null, assessmentError: 'HAZARD_PROVIDER_TIMEOUT' } }));
  await page.goto('/routes'); await choose(page); await page.getByRole('button', { name: 'Analyze route', exact: true }).click();
  await expect(page.getByRole('heading', { name: '112.0 km driving route' })).toBeVisible();
  await expect(page.getByText(/Hazard evidence unavailable/)).toBeVisible();
  await expect(page.getByText(/No currently retrieved monitored earthquakes/)).toHaveCount(0);
});
