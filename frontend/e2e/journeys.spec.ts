import { authFixture } from './authFixture';
import { test, expect } from '@playwright/test';
test.beforeEach(async ({ page }) => { await page.route('**/api/v1/auth/**', route => authFixture(route) || route.fallback()); });
const time = '2026-01-01T00:00:00Z';
const id = '11111111-1111-4111-8111-111111111111';
const routeInput = { points: [{ latitude: 0, longitude: 0 }, { latitude: 0, longitude: 2 }], corridorKm: 25, days: 7, minMagnitude: 4 };
const assessment = { modelVersion: 'route-proximity-v1', query: routeInput, assessedAt: time, lengthKm: 222, overallRisk: 'UNKNOWN', truncated: false, matches: [], explanation: 'Test-only proximity evidence. No safe-route recommendation.' };
test('history saves and reloads evidence; incomplete history withholds frequency', async ({ page }) => {
  const snapshot = { id, modelVersion: 'catalog-frequency-v1', query: { latitude: 0, longitude: 0, radiusKm: 100, from: '2025-01-01T00:00:00Z', to: time, minMagnitude: 4 }, fetchedAt: time, eventCount: 2000, exposureYears: 1, annualFrequency: null, truncated: true, events: [], source: { attribution: 'USGS', documentationUrl: 'https://earthquake.usgs.gov/fdsnws/event/1/' }, explanation: 'Test truncated evidence; not a forecast.' };
  const methods: string[] = [];
  await page.route('**/api/v1/history/earthquakes**', r => { methods.push(r.request().method()); return r.fulfill({ json: snapshot }); });
  await page.goto('/history');
  await page.getByLabel('Latitude', { exact: true }).fill('0'); await page.getByLabel('Longitude', { exact: true }).fill('0');
  await page.getByLabel('From · UTC, inclusive').fill('2025-01-01'); await page.getByLabel('To · UTC, exclusive').fill('2026-01-01');
  await page.getByRole('button', { name: 'Retrieve & save history' }).click();
  await expect(page.getByText('At least 2000 matching earthquakes')).toBeVisible();
  await expect(page.getByText('Unavailable — incomplete catalog response')).toBeVisible();
  await page.getByRole('button', { name: 'Load snapshot' }).click();
  await expect(page.getByRole('region', { name: 'Historical result' })).toBeVisible();
  expect(methods).toEqual(['POST', 'GET']);
});
test('journey lifecycle exposes scheduler status, checks, failures and deletion', async ({ page }) => {
  let saved = false; let checked = false;
  let journey = { id, name: 'Test journey', route: routeInput, status: 'PAUSED', createdAt: time, lastAttemptAt: null as string | null, lastCheckedAt: null, lastError: null as string | null, assessment: null as typeof assessment | null };
  await page.route('**/api/v1/**', async r => {
    const auth = authFixture(r); if (auth) return auth;
    const path = new URL(r.request().url()).pathname; const method = r.request().method();
    if (path.endsWith('/monitoring')) return r.fulfill({ json: { schedulerEnabled: false, intervalMs: 300000 } });
    if (path.endsWith('/notices')) return r.fulfill({ json: [] });
    if (path.endsWith('/assessment')) return r.fulfill({ json: assessment });
    if (path.endsWith('/check')) { checked = true; journey = { ...journey, lastAttemptAt: time, lastError: 'PROVIDER_TIMEOUT' }; return r.fulfill({ json: journey }); }
    if (method === 'PATCH') { journey = { ...journey, status: r.request().postDataJSON().status }; return r.fulfill({ json: journey }); }
    if (method === 'DELETE') { saved = false; return r.fulfill({ status: 204 }); }
    if (method === 'POST') { saved = true; return r.fulfill({ status: 201, json: journey }); }
    return r.fulfill({ json: saved ? [journey] : [] });
  });
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/routes');
  await expect(page.getByText('Scheduled checks are disabled', { exact: false })).toBeVisible();
  await page.getByText('Manual waypoint mode', { exact: true }).click();
  await page.getByLabel('Journey name').fill('Test journey'); await page.getByLabel('Waypoints').fill('0,0\n0,2');
  await page.getByRole('button', { name: 'Assess route', exact: true }).click();
  await expect(page.getByText('Route context · UNKNOWN')).toBeVisible();
  await page.getByRole('button', { name: 'Save paused journey' }).click();
  await expect(page.getByRole('button', { name: 'Check now' })).toBeDisabled();
  await page.getByRole('button', { name: 'Activate journey' }).click();
  await page.getByRole('button', { name: 'Check now' }).click();
  await expect(page.getByRole('region', { name: 'Selected journey' }).getByRole('alert')).toContainText('PROVIDER_TIMEOUT'); expect(checked).toBe(true);
  await page.getByRole('button', { name: 'Pause journey' }).click();
  await expect(page.getByRole('button', { name: 'Check now' })).toBeDisabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Delete saved journey' }).click();
  await expect(page.getByRole('region', { name: 'Selected journey' })).toHaveCount(0);
});
test('multi-hazard journey shows independent coverage, evidence and revision notices on mobile', async ({ page }) => {
  const event = { externalId: 'flood-1', source: 'GDACS', title: 'Fixture flood advisory', sourceUrl: 'https://www.gdacs.org/', startedAt: time, latitude: 0, longitude: 1, measurements: {}, hazardType: 'FLOOD', informationType: 'ADVISORY', severity: 'HIGH', confidence: 'UNKNOWN', retrievedAt: time, sourceUpdatedAt: time };
  const evidence = { event, distanceKm: 3, segmentIndex: 0 };
  const journey = { id, name: 'Multi-hazard trip', route: routeInput, status: 'ACTIVE', createdAt: time, lastAttemptAt: time, lastCheckedAt: null, lastError: 'INCOMPLETE_COVERAGE',
    providerCheckpoints: [{ provider: 'GDACS', lastSuccessfulCheck: time }], assessment: { ...assessment, coverageStatus: 'PARTIAL', matches: [evidence], providerStatuses: [{ provider: 'GDACS', coverageStatus: 'PARTIAL', retrievedAt: time, limitations: ['Recent advisory feed only'] }, { provider: 'FIRMS', coverageStatus: 'UNAVAILABLE', retrievedAt: null, limitations: ['PROVIDER_UNAVAILABLE'] }] } };
  await page.route('**/api/v1/**', async r => {
    const auth = authFixture(r); if (auth) return auth;
    const path = new URL(r.request().url()).pathname;
    if (path.endsWith('/monitoring')) return r.fulfill({ json: { schedulerEnabled: false, intervalMs: 300000 } });
    if (path.endsWith('/notices')) return r.fulfill({ json: [{ id, journeyId: id, createdAt: time, type: 'UPDATED_MATCHING_EVIDENCE', stateVersion: 2, evidence, explanation: 'Provider severity changed. Informational notice, not an official warning.' }] });
    return r.fulfill({ json: [journey] });
  });
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/routes');
  await page.getByRole('button', { name: 'Multi-hazard trip · ACTIVE' }).click();
  const selected = page.getByRole('region', { name: 'Selected journey' });
  await expect(selected.getByText('Data coverage: PARTIAL')).toBeVisible();
  await expect(selected.getByText('FIRMS · UNAVAILABLE', { exact: false })).toBeVisible();
  await expect(selected.getByText('FLOOD: 1 matching evidence items · risk UNKNOWN')).toBeVisible();
  await expect(selected.getByText('GDACS last successful evidence check:', { exact: false })).toBeVisible();
  await expect(selected.getByText('UPDATED_MATCHING_EVIDENCE', { exact: false })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
