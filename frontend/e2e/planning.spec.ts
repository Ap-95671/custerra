import {areas} from './intelligenceFixture';
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
test('route draft survives workspace navigation and clears on session expiry', async ({ page }) => {
  await setup(page);
  await page.goto('/routes');
  await expect(page.getByRole('link', { name: 'Account: fixture_user' })).toBeVisible();
  await choose(page);
  await page.getByLabel('Route corridor · km').fill('12');
  const navigation = page.getByRole('navigation', { name: 'Explore Custerra' });
  await navigation.getByRole('link', { name: 'Insights' }).click();
  await navigation.getByRole('link', { name: 'Routes' }).click();
  await expect(page.getByText('Selected from · Delhi, India')).toBeVisible();
  await expect(page.getByText('Selected to · Manali, India')).toBeVisible();
  await expect(page.getByLabel('Route corridor · km')).toHaveValue('12');
  await expect(page.getByRole('button', { name: 'Analyze route', exact: true })).toBeEnabled();
  await page.evaluate(() => window.dispatchEvent(new Event('custerra-session-expired')));
  await expect(page.getByLabel('From', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('Route corridor · km')).toHaveValue('25');
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
  await page.getByRole('region',{name:'Driving route result'}).screenshot({path:`../tmp/v3/screenshots/route-result-${width}.png`});
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
test('selecting an exposed segment focuses the existing geometry without another provider request',async({page})=>{
  await setup(page);let requests=0;
  const event={externalId:'segment-fixture',source:'USGS',title:'Synthetic segment observation',sourceUrl:'https://earthquake.usgs.gov/',startedAt:time,latitude:0,longitude:.5,measurements:{magnitude:4.2}};
  await page.route('**/api/v1/routes/plan',route=>{requests++;return route.fulfill({json:{...planned,assessment:{...planned.assessment,matches:[{event,distanceKm:1,segmentIndex:0}]}}});});
  await page.goto('/routes');await choose(page);await page.getByRole('button',{name:'Analyze route',exact:true}).click();
  await page.getByRole('button',{name:'Inspect segment 1',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'Inspecting original segment 1'})).toBeVisible();
  await expect(page.locator('.selected-route-segment')).toHaveCount(1);expect(requests).toBe(1);
});

 test('provider alternatives retain geometry and source-area selection uses original segments',async({page})=>{
  await setup(page);let plans=0;
  await page.route('**/api/v1/routes/alternatives',r=>{plans++;return r.fulfill({json:{routes:[planned,{...planned,geometry:'??_c`|@_c`|@~b`|@_c`|@',distanceKm:270,durationSeconds:9000,segmentCount:2}],retrievedAt:time,limitations:['Synthetic fixture, no safety ranking.']}});});
  await page.route('**/api/v1/intelligence/route',r=>r.fulfill({json:areas}));
  await page.goto('/routes');await choose(page);await page.getByRole('button',{name:'Compare provider routes'}).click();
  await expect(page.getByText('Provider returned 2 route(s).',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:/Route 2 ·/}).click();await expect(page.getByRole('heading',{name:'270.0 km driving route'})).toBeVisible();
  await page.getByRole('button',{name:'Check source areas'}).click();await expect(page.getByText('12.50 km',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'Inspect source segment 1'}).click();await expect(page.locator('.selected-route-segment')).toHaveCount(1);expect(plans).toBe(1);
 });
test('late source-area evidence cannot overwrite a different provider route',async({page})=>{
 await setup(page);let areaStarted=false;
 await page.route('**/api/v1/routes/alternatives',r=>r.fulfill({json:{routes:[planned,{...planned,geometry:'??_c`|@_c`|@~b`|@_c`|@',distanceKm:270,segmentCount:2}],retrievedAt:time,limitations:[]}}));
 await page.route('**/api/v1/intelligence/route',async r=>{areaStarted=true;await new Promise(resolve=>setTimeout(resolve,700));await r.fulfill({json:areas}).catch(()=>{});});
 await page.goto('/routes');await choose(page);await page.getByRole('button',{name:'Compare provider routes'}).click();await page.getByRole('button',{name:'Check source areas'}).click();await expect.poll(()=>areaStarted).toBe(true);
 await page.getByRole('button',{name:/Route 2 ·/}).click();await page.waitForTimeout(900);
 await expect(page.getByRole('heading',{name:'270.0 km driving route'})).toBeVisible();await expect(page.getByText(areas.items[0].event.title,{exact:true})).toHaveCount(0);
});

test('closure checking is explicit, attributed, and keeps source proximity distinct from a closed route', async ({ page }) => {
  await setup(page);
  const sourceUrl = 'https://quickmap.dot.ca.gov/';
  let providerReads = 0;
  await page.route('**/api/v1/intelligence/road-closures', route => {
    expect(route.request().postDataJSON()).toEqual({ geometry: planned.geometry });
    providerReads++;
    const status = { rejectedRecords: 0, truncated: false, retrievedAt: time, sourceUpdatedAt: time, sourceUrl };
    return route.fulfill({ json: providerReads > 1 ? { ...status, coverage: 'STALE', items: [] } : {
      ...status, coverage: 'PARTIAL', items: [{
        id: 'fixture', reportedRoad: 'US-101', direction: 'North', facility: 'Freeway', type: 'Lane', work: 'Maintenance',
        state: 'ACTIVE', scheduledStart: time, scheduledEnd: null, sourceUpdatedAt: time,
        endpoint: 'END', endpointName: 'Example junction', segmentIndex: 0, distanceKm: 0.12,
      }],
    } });
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/routes');
  await choose(page);
  await page.getByRole('button', { name: 'Analyze route', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Caltrans closure reports' });
  await expect(panel).toBeVisible();
  expect(providerReads).toBe(0);
  const action = panel.getByRole('button', { name: 'Check Caltrans closure reports' });
  await action.click();
  await expect(panel.getByRole('link', { name: 'Open Caltrans QuickMap ↗' })).toHaveAttribute('href', sourceUrl);
  await expect(panel.getByText('120 m from route segment 1', { exact: false })).toBeVisible();
  await expect(panel.getByText('Reported started', { exact: false })).toBeVisible();
  await panel.getByRole('button', { name: 'Focus near US-101 on map' }).click();
  await expect(page.getByText('Inspecting original segment 1.', { exact: false })).toBeVisible();
  await action.click();
  await expect(panel.getByText('The closure feed is more than 30 minutes old.', { exact: false })).toBeVisible();
  await expect(panel.getByText('Reported started', { exact: false })).toHaveCount(0);
  expect(providerReads).toBe(2);
});
