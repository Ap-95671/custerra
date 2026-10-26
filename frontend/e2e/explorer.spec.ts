import { test, expect, type Page } from '@playwright/test';
test.beforeEach(async ({ page }) => {
  await page.route('https://tile.openstreetmap.org/**', route => route.abort());
});
const timestamp = '2026-01-01T00:00:00Z';
const event = { externalId: 'test-only', source: 'USGS', title: 'Test-only earthquake fixture', hazardType: 'EARTHQUAKE', informationType: 'OBSERVED_EVENT', latitude: 32.25, longitude: 77.2, sourceUrl: 'https://earthquake.usgs.gov/earthquakes/eventpage/test', startedAt: timestamp, severity: 'UNKNOWN', measurements: { magnitude: 4.2 } };
const nearby = { fetchedAt: timestamp, truncated: false, coverage: 'Test', sources: [], items: [{ event, distanceKm: 5 }] };
const weather = { query: { latitude: 32.24, longitude: 77.19 }, gridLatitude: 32.25, gridLongitude: 77.2, informationType: 'MODELED_WEATHER', validAt: timestamp, fetchedAt: timestamp, intervalSeconds: 900, temperatureC: 12.5, rainMm: 0, precipitationMm: 0, windSpeedKmh: 8, partial: false, stale: false, source: { id: 'OPEN_METEO', attribution: 'Open-Meteo', documentationUrl: 'https://open-meteo.com/en/docs', licensingUrl: 'https://open-meteo.com/en/terms', refreshPolicy: 'test' }, license: 'CC BY 4.0', limitations: [] };
const risk = { modelVersion: 'test-experimental', assessedAt: timestamp, overallRisk: 'UNKNOWN', score: null, confidence: 'UNKNOWN', explanation: 'Test', safetyNotice: 'Test', sourceResultsTruncated: false, dataSources: [], hazards: [{ hazardType: 'EARTHQUAKE', scope: 'RECENT_EARTHQUAKE_ACTIVITY', modelVersion: 'test', riskLevel: 'UNKNOWN', score: null, confidence: 'UNKNOWN', explanation: 'Insufficient complete observations.', limitations: [], factors: [] }] };
async function intercept(page: Page, failRisk = false, empty = false) {
  await page.route('**/api/v1/**', route => route.fulfill({ status: route.request().url().includes('/risk/') && failRisk ? 502 : 200, contentType: 'application/json', body: JSON.stringify(route.request().url().includes('/weather/') ? weather : route.request().url().includes('/risk/') ? risk : empty ? { ...nearby, items: [] } : nearby) }));
}
test('location selection, map details, layer toggle and unknown score', async ({ page }) => {
  await intercept(page); await page.goto('/');
  await expect(page.getByText('Select a point to begin')).toBeVisible();
  await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByRole('button', { name: event.title })).toBeVisible();
  await expect(page.locator('.score > strong')).toHaveText('—');
  await expect(page.locator('.overall strong')).toHaveText('UNKNOWN');
  await page.getByRole('button', { name: event.title }).click();
  await expect(page.locator('.leaflet-popup')).toBeVisible();
  await page.getByLabel('Earthquake observations', { exact: true }).uncheck();
  await expect(page.locator('.leaflet-popup')).toHaveCount(0);
  await expect(page.getByRole('button', { name: event.title })).toBeVisible();
});
test('independent risk failure preserves observations and exposes retry', async ({ page }) => {
  await intercept(page, true); await page.goto('/');
  await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByRole('button', { name: event.title })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Retry assessment' })).toBeVisible();
  await expect(page.getByText('The hazard provider is unavailable. Please try again.')).toBeVisible();
});
test('empty is not safe; mobile has no horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await intercept(page, false, true); await page.goto('/');
  await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByText('No matching hazards in the retrieved evidence.')).toBeVisible();
  await expect(page.getByText('This does not establish safety or rule out other hazards.')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('new selection cannot be overwritten by delayed prior request', async ({ page }) => {
  await page.route('**/api/v1/**', async route => {
    const old = route.request().url().includes('latitude=32.24&');
    if (old) await new Promise(resolve => setTimeout(resolve, 700));
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(route.request().url().includes('/weather/') ? { ...weather, temperatureC: old ? 99 : 12.5 } : route.request().url().includes('/risk/') ? risk : { ...nearby, items: [{ event: { ...event, title: old ? 'Old result' : 'Latest result' }, distanceKm: 5 }] }) }).catch(() => {});
  });
  await page.goto('/'); await page.getByRole('button', { name: 'Manali' }).click(); await page.getByRole('button', { name: 'Tokyo' }).click();
  await expect(page.getByRole('button', { name: 'Latest result' })).toBeVisible();
  await page.waitForTimeout(900);
  await expect(page.getByRole('button', { name: 'Old result' })).toHaveCount(0);
  await expect(page.getByText('12.5 °C')).toBeVisible();
  await expect(page.getByText('99.0 °C')).toHaveCount(0);
});
test('loading state precedes numeric activity and truncation warnings', async ({ page }) => {
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/**', async route => {
    await pending;
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(route.request().url().includes('/weather/') ? weather : route.request().url().includes('/risk/') ? { ...risk, hazards: [{ ...risk.hazards[0], riskLevel: 'MODERATE', score: 34, confidence: 'LOW' }] } : { ...nearby, truncated: true }) });
  });
  await page.goto('/'); await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByText('Assessing recent activity…')).toBeVisible();
  await expect(page.getByText('Retrieving hazard evidence…')).toBeVisible();
  release();
  await expect(page.locator('.score > strong')).toHaveText('34/100');
  await expect(page.locator('.overall strong')).toHaveText('UNKNOWN');
  await expect(page.getByText(/Source results were truncated/)).toBeVisible();
});
test('clicking map chooses a location; clicking an event does not refetch', async ({ page }) => {
  let requests = 0;
  await page.route('**/api/v1/**', route => { if (route.request().url().includes('/auth/')) return route.fulfill({ status: 401, json: {} }); requests++; return route.fulfill({ contentType: 'application/json', body: JSON.stringify(route.request().url().includes('/weather/') ? weather : route.request().url().includes('/risk/') ? risk : nearby) }); });
  await page.goto('/');
  await page.locator('.map-canvas').click({ position: { x: 150, y: 180 } });
  await expect(page.getByRole('button', { name: event.title })).toBeVisible();
  expect(requests).toBe(3);
  await page.getByRole('button', { name: event.title }).click();
  await expect(page.locator('.leaflet-popup')).toBeVisible();
  await page.locator('.leaflet-interactive').last().click({ force: true });
  expect(requests).toBe(3);
});

test('weather displays interval units and missing/stale values explicitly', async ({ page }) => {
  await intercept(page);
  await page.route('**/api/v1/weather/**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ...weather, rainMm: null, partial: true, stale: true }) }));
  await page.goto('/'); await page.getByRole('button', { name: 'Manali' }).click();
  const panel = page.getByRole('region', { name: 'Weather context' });
  await expect(panel.getByText('12.5 °C')).toBeVisible();
  await expect(panel.getByText('Unavailable', { exact: true })).toBeVisible();
  await expect(panel.getByText('Rain · preceding 15 min', { exact: true })).toBeVisible();
  await expect(panel.getByText(/more than two hours old/)).toBeVisible();
  await expect(panel.getByText(/missing values are not zero/)).toBeVisible();
  await expect(page.locator('.overall strong')).toHaveText('UNKNOWN');
});
test('weather failure preserves hazard data and weather retry recovers', async ({ page }) => {
  await intercept(page);
  await page.route('**/api/v1/weather/**', route => route.fulfill({ status: 504, body: '' }));
  await page.goto('/'); await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByRole('button', { name: event.title })).toBeVisible();
  await expect(page.getByText('The weather provider timed out. Please try again.')).toBeVisible();
  await page.unroute('**/api/v1/weather/**');
  await page.getByRole('button', { name: 'Retry weather' }).click();
  await expect(page.getByText('12.5 °C')).toBeVisible();
});

test('multi-hazard layers preserve evidence, distinguish advisories and show partial source coverage', async ({ page }) => {
  await intercept(page);
  const thermal = { ...event, externalId: 'thermal-test', source: 'FIRMS', hazardType: 'WILDFIRE', title: 'Satellite-detected thermal anomaly', confidence: 'MEDIUM', measurements: { frp: 10 }, attributes: { confidence: 'n' } };
  const flood = { ...event, externalId: 'FL:123', source: 'GDACS', hazardType: 'FLOOD', informationType: 'ADVISORY', title: 'Test-only flood advisory', confidence: 'UNKNOWN', latitude: 32.3, severity: 'MODERATE', measurements: {}, attributes: { alertlevel: 'Orange' } };
  await page.route('**/api/v1/hazards/nearby**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ...nearby,
    coverageStatus: 'PARTIAL', providerStatuses: [{ provider: 'USGS', coverageStatus: 'UNAVAILABLE', retrievedAt: null, limitations: ['PROVIDER_TIMEOUT'] }, { provider: 'FIRMS', coverageStatus: 'COMPLETE', retrievedAt: timestamp, limitations: [] }, { provider: 'GDACS', coverageStatus: 'PARTIAL', retrievedAt: timestamp, limitations: ['RECENT_FEED_ONLY'] }],
    items: [{ event: thermal, distanceKm: 5 }, { event: flood, distanceKm: 7 }],
  }) }));
  await page.goto('/'); await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByText('USGS: UNAVAILABLE')).toBeVisible();
  await expect(page.getByText('Selected source coverage: PARTIAL')).toBeVisible();
  await page.getByRole('button', { name: thermal.title }).click();
  await expect(page.locator('.leaflet-popup')).toContainText('not a confirmed wildfire');
  await expect(page.locator('.leaflet-popup')).toContainText('Confidence: MEDIUM');
  await page.getByLabel('Fire / Thermal Anomalies', { exact: true }).uncheck();
  await expect(page.locator('.leaflet-popup')).toHaveCount(0);
  await expect(page.getByRole('button', { name: thermal.title })).toBeVisible();
  await page.getByRole('button', { name: flood.title }).click();
  await expect(page.locator('.leaflet-popup')).toContainText('advisory');
  await page.getByLabel('Global Disaster Events', { exact: true }).uncheck();
  await expect(page.locator('.leaflet-popup')).toHaveCount(0);
  await expect(page.getByRole('button', { name: flood.title })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('all sources unavailable is never displayed as an empty successful search', async ({ page }) => {
  await intercept(page);
  await page.route('**/api/v1/hazards/nearby**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ...nearby, items: [], coverageStatus: 'UNAVAILABLE', providerStatuses: [{ provider: 'FIRMS', coverageStatus: 'UNAVAILABLE', retrievedAt: null, limitations: ['NOT_CONFIGURED'] }] }) }));
  await page.goto('/'); await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByText('Hazard data is unavailable. No assessment of hazard absence can be made.')).toBeVisible();
  await expect(page.getByText('No matching hazards in the retrieved evidence.')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Retry observations' })).toBeVisible();
});

test('multi-hazard assessment presents thermal context without converting overall unknown to low', async ({ page }) => {
  await intercept(page);
  await page.route('**/api/v1/risk/multi-hazard**', route => route.fulfill({ json: { ...risk, dominantHazards: ['WILDFIRE'], coverageStatus: 'PARTIAL', weatherStatus: 'AVAILABLE', providerStatuses: [{ provider: 'FIRMS', coverageStatus: 'COMPLETE', retrievedAt: timestamp, limitations: [] }], hazards: [...risk.hazards, { hazardType: 'WILDFIRE', scope: 'RECENT_THERMAL_DETECTION_PROXIMITY', modelVersion: 'thermal-proximity-v1-experimental', riskLevel: 'HIGH', score: 60, confidence: 'LOW', explanation: 'Experimental thermal proximity, not wildfire risk or a prediction.', limitations: ['THERMAL_ANOMALIES_NOT_CONFIRMED_WILDFIRES'], factors: [], environmentalContext: [{ code: 'MODELED_WIND_CONTEXT', value: 30, unit: 'km/h', source: 'OPEN_METEO', validAt: timestamp, retrievedAt: timestamp, explanation: 'Context only; no score change or spread inference.' }] }] } }));
  await page.goto('/'); await page.getByRole('button', { name: 'Manali' }).click();
  await expect(page.getByText('Thermal detection proximity', { exact: true })).toBeVisible();
  await expect(page.locator('.hazard-score')).toContainText('60/100');
  await expect(page.locator('.overall strong')).toHaveText('UNKNOWN');
  await expect(page.getByText(/no score change or spread inference/)).toBeVisible();
});
