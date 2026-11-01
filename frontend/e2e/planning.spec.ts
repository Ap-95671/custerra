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

for (const width of [1280, 390]) test(`generated journey save, progress privacy and completion at ${width}px`, async ({ page }) => {
  await setup(page); await page.setViewportSize({width,height:844});
  const input={points:[{latitude:0,longitude:0},{latitude:0,longitude:1}],corridorKm:25,days:7,minMagnitude:4};
  const id='33333333-3333-4333-8333-333333333333';
  let saved=false;
  let journey: Record<string,unknown>={id,name:'Delhi to Manali',route:input,status:'PLANNED',createdAt:time,lastAttemptAt:null,lastCheckedAt:null,lastError:null,assessment:null,progress:null,generatedGeometry:{encodedPolyline:planned.geometry,version:1,originName:'Delhi',destinationName:'Manali'}};
  const positions: unknown[]=[];
  await page.addInitScript(()=>{Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition:(_success:unknown,error:(value:unknown)=>void)=>error({code:1})}});});
  await page.route('**/api/v1/journeys**',async r=>{
    const path=new URL(r.request().url()).pathname,method=r.request().method();
    if(path.endsWith('/monitoring'))return r.fulfill({json:{schedulerEnabled:false,intervalMs:300000}});
    if(path.endsWith('/notices'))return r.fulfill({json:[]});
    if(path.endsWith('/generated')){expect(r.request().postDataJSON()).toMatchObject({geometry:planned.geometry,originName:'Delhi',destinationName:'Manali'});saved=true;return r.fulfill({status:201,json:journey});}
    if(path.endsWith('/progress')){
      if(method==='PUT'){const p=r.request().postDataJSON();positions.push(p);journey={...journey,progress:{...p,segmentIndex:0,completedFraction:0,distanceFromRouteKm:0,state:'MATCHED'}};}
      else journey={...journey,progress:null};
      return r.fulfill({json:journey});
    }
    if(method==='PATCH'){journey={...journey,status:r.request().postDataJSON().status,progress:null};return r.fulfill({json:journey});}
    return r.fulfill({json:saved?[journey]:[]});
  });
  await page.goto('/routes');await choose(page);await page.getByRole('button',{name:'Analyze route',exact:true}).click();
  await page.getByRole('button',{name:'Save Journey',exact:true}).click();
  const selected=page.getByRole('region',{name:'Selected journey'});
  await expect(selected).toContainText('PLANNED');expect(positions).toHaveLength(0);
  await selected.getByRole('button',{name:'Start Journey',exact:true}).click();
  await selected.getByRole('button',{name:'Share current location once',exact:true}).click();
  await expect(selected.getByRole('alert')).toContainText('Location permission was denied');expect(positions).toHaveLength(0);
  await selected.getByText('Enter current position manually',{exact:true}).click();
  await selected.getByLabel('Current latitude',{exact:true}).fill('0');await selected.getByLabel('Current longitude',{exact:true}).fill('0.5');await selected.getByLabel('Position accuracy · meters').fill('10');
  await selected.getByRole('button',{name:'Share entered position once'}).click();
  await expect(selected).toContainText('Remaining-route monitoring starts at segment 1');expect(positions).toHaveLength(1);
  await selected.getByRole('button',{name:'Clear shared position'}).click();await expect(selected).toContainText('Full-route monitoring:');
  await selected.getByRole('button',{name:'Complete Journey',exact:true}).click();await expect(selected).toContainText('COMPLETED');
  await expect(selected.getByRole('button',{name:'Share current location once',exact:true})).toBeDisabled();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
