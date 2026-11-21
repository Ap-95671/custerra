import { test, expect, type Page } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import { authFixture } from './authFixture';
const date='2026-11-01T12:00:00Z';
const source={id:'USGS',attribution:'U.S. Geological Survey',documentationUrl:'https://earthquake.usgs.gov/fdsnws/event/1/',licensingUrl:'https://www.usgs.gov/',refreshPolicy:'Synthetic development fixture'};
const event={externalId:'v3-fixture',source:'USGS',title:'Development fixture — earthquake observation',hazardType:'EARTHQUAKE',informationType:'OBSERVED_EVENT',latitude:32.25,longitude:77.2,sourceUrl:'https://earthquake.usgs.gov/',startedAt:date,severity:'UNKNOWN',confidence:'UNKNOWN',measurements:{magnitude:4.2,depthKm:12}};
const nearby={fetchedAt:date,truncated:false,coverage:'Synthetic fixture; no live provider was queried.',sources:[source],coverageStatus:'PARTIAL',providerStatuses:[{provider:'USGS',coverageStatus:'COMPLETE',retrievedAt:date,limitations:[]},{provider:'FIRMS',coverageStatus:'UNAVAILABLE',retrievedAt:null,limitations:['NOT_CONFIGURED']}],items:[{event,distanceKm:5}]};
const risk={modelVersion:'fixture-experimental',assessedAt:date,overallRisk:'UNKNOWN',score:null,confidence:'UNKNOWN',explanation:'Available evidence does not establish all-hazard risk.',safetyNotice:'Development fixture.',sourceResultsTruncated:false,dataSources:[source],hazards:[{hazardType:'EARTHQUAKE',scope:'RECENT_EARTHQUAKE_ACTIVITY',modelVersion:'fixture',riskLevel:'MODERATE',score:34,confidence:'LOW',explanation:'Synthetic activity indicator, not a prediction.',limitations:[],factors:[]}]};
const id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
async function fixture(page:Page) {
  await page.route('https://tile.openstreetmap.org/**',route=>route.abort());
  await page.route('**/api/v1/**',route=>{
    const auth=authFixture(route);if(auth)return auth;
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/hazards/nearby'))return route.fulfill({json:nearby});
    if(path.includes('/risk/'))return route.fulfill({json:risk});
    if(path.includes('/weather/'))return route.fulfill({status:502,json:{}});
    if(path.endsWith('/intelligence/nearby'))return route.fulfill({json:{retrievedAt:date,coverageStatus:'UNAVAILABLE',items:[],sources:[{source:'NWS',retrievedAt:null,coverageStatus:'UNAVAILABLE',limitations:['Synthetic unavailable-source fixture']}],correlations:[],limitations:[]}});
    if(path.endsWith('/locations/configuration'))return route.fulfill({json:{schedulerEnabled:false}});
    if(path.endsWith('/monitoring'))return route.fulfill({json:{schedulerEnabled:false,intervalMs:300000}});
    if(path.endsWith('/preferences'))return route.fulfill({json:{enabled:true,pushEnabled:false,hazardTypes:['EARTHQUAKE'],minimumSeverity:'UNKNOWN',includeUnknown:true}});
    if(path.endsWith('/push'))return route.fulfill({json:{configured:false,dispatcherEnabled:false,publicKey:null}});
    if(path.endsWith('/developer/usage'))return route.fulfill({json:{organizationId:id,plan:'DEVELOPMENT',dailyLimit:100,minuteLimit:20,requestsToday:3}});
    return route.fulfill({json:[]});
  });
}
test('public workflow chapters support keyboard and launch a real operational route',async({page})=>{
  await fixture(page);let providers=0;page.on('request',r=>{if(r.url().includes('/hazards/')||r.url().includes('/risk/'))providers++;});
  await page.goto('/');await page.getByRole('tab',{name:/Discover/}).focus();await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('tab',{name:/Understand/})).toHaveAttribute('aria-selected','true');
  await page.getByRole('tab',{name:/Plan/}).click();await expect(page.getByRole('link',{name:'Plan a route',exact:true})).toHaveAttribute('href','/routes');
  await page.getByRole('tab',{name:/Monitor/}).click();
  await expect(page.getByRole('img',{name:/An active journey in Custerra/})).toBeVisible();
  await page.keyboard.press('End');
  await expect(page.getByRole('tab',{name:/Review/})).toHaveAttribute('aria-selected','true');
  expect(providers).toBe(0);await page.getByRole('link',{name:'Launch application',exact:true}).click();await expect(page).toHaveURL(/\/map$/);
});
test('in-memory explorer evidence, layer selection and camera survive client navigation without refetch',async({page})=>{
  await fixture(page);let providers=0;page.on('request',r=>{if(/\/(hazards|risk|weather)\//.test(r.url()))providers++;});
  await page.goto('/map');await page.getByRole('button',{name:'Manali',exact:true}).click();await page.getByRole('button',{name:event.title}).click();
  const queryRadius = page.locator('.map-canvas path[stroke="#b2c8ba"]');
  await expect(queryRadius).toHaveCount(1);
  await page.getByLabel('Fire / Thermal Anomalies',{exact:true}).uncheck();
  expect(providers).toBe(3);await page.getByRole('link',{name:'Routes',exact:true}).click();await page.getByRole('link',{name:'Explore',exact:true}).click();
  await expect(page.getByRole('button',{name:event.title})).toBeVisible();await expect(page.getByLabel('Fire / Thermal Anomalies',{exact:true})).not.toBeChecked();expect(providers).toBe(3);
  await expect(queryRadius).toHaveCount(1);
  await expect(page.getByRole('tabpanel',{name:'Selection'})).toContainText(event.title);
});
test('mobile rail is keyboard-hidden while closed and Escape dismisses navigation',async({page})=>{
  await page.setViewportSize({width:360,height:760});await fixture(page);await page.goto('/map');
  await expect(page.getByRole('navigation',{name:'Explore Custerra'})).toHaveCount(0);
  await page.getByRole('button',{name:'Open navigation'}).click();await expect(page.getByRole('link',{name:'Journeys',exact:true})).toBeVisible();
  await page.keyboard.press('Escape');await expect(page.getByRole('navigation',{name:'Explore Custerra'})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Open navigation'})).toBeFocused();
});
test('desktop evidence inspection releases map space and restores keyboard focus',async({page})=>{
  await fixture(page);
  await page.setViewportSize({width:1440,height:900});
  await page.goto('/map');
  const map=page.locator('.map-canvas');
  await expect(map).toBeVisible();
  const initial=await map.evaluate(element=>element.clientWidth);
  await page.getByRole('button',{name:'Hide evidence panel'}).click();
  await expect(page.getByRole('button',{name:'Show evidence panel'})).toBeFocused();
  await expect.poll(()=>map.evaluate(element=>element.clientWidth)).toBeGreaterThan(initial);
  await page.getByRole('button',{name:'Show evidence panel'}).press('Enter');
  await expect(page.getByRole('button',{name:'Hide evidence panel'})).toBeFocused();
  await expect.poll(()=>map.evaluate(element=>element.clientWidth)).toBe(initial);
});
test('developer key flow uses existing scopes/CSRF, one-time reveal and confirmed revocation',async({page})=>{
  await fixture(page);let active=false;const writes:string[]=[];const key='cst_SYNTHETIC_DEVELOPMENT_KEY_NOT_A_CREDENTIAL';
  await page.route('**/api/v1/developer/clients**',route=>{
    const method=route.request().method();if(method!=='GET'){writes.push(method);expect(route.request().headers()['x-csrf-token']).toBe('fixture-token');}
    if(method==='POST'){expect(route.request().postDataJSON()).toMatchObject({name:'Test integration',scopes:['nearby']});active=true;return route.fulfill({status:201,json:{id,apiKey:key,expiresAt:'2027-01-01T00:00:00Z'}});}
    if(method==='DELETE'){active=false;return route.fulfill({status:204});}
    return route.fulfill({json:active?[{id,name:'Test integration',suffix:'FIXTURE',createdAt:date,expiresAt:'2027-01-01T00:00:00Z',revokedAt:null,lastUsedAt:null,scopes:['nearby']}]:[]});
  });
  await page.goto('/developer');await page.getByLabel('Key name').fill('Test integration');await page.getByRole('button',{name:'Create key',exact:true}).click();
  await expect(page.getByRole('region',{name:'One-time API key'})).toContainText(key);await page.getByRole('button',{name:'I have stored it - dismiss'}).click();
  await expect(page.getByText(key,{exact:true})).toHaveCount(0);expect(await page.evaluate(()=>JSON.stringify(localStorage))).not.toContain(key);
  await page.getByRole('button',{name:'Revoke',exact:true}).click();expect(writes).toEqual(['POST']);await page.getByRole('button',{name:'Confirm revoke',exact:true}).click();await expect.poll(()=>writes).toEqual(['POST','DELETE']);
});
for(const width of [1440,1280,1024,768,390,360])test(`visual layouts and no horizontal overflow at ${width}px`,async({page})=>{
  await fixture(page);await page.setViewportSize({width,height:900});mkdirSync('../tmp/v3/screenshots',{recursive:true});
  const samples=[];
  for(const path of ['/','/map','/routes','/journeys','/alerts','/history','/insights','/locations','/developer','/settings','/login','/register','/offline.html']){
    await page.goto(path);if(path==='/map'){await page.getByRole('button',{name:'Manali',exact:true}).click();await expect(page.getByRole('button',{name:event.title})).toBeVisible();}
    await expect(page.getByRole('heading',{level:1})).toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${path} at ${width}`).toBe(true);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await page.screenshot({path:`../tmp/v3/screenshots/${path==='/'?'landing':path.slice(1).replace('.html','')}-${width}.png`,fullPage:true});
    samples.push({path,...await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,width:innerWidth,domNodes:document.querySelectorAll('*').length}))});
  }
  writeFileSync(`../tmp/v3/layout-${width}.json`,JSON.stringify(samples,null,2));
});
test('light appearance, reduced motion, short viewport and zoom remain usable',async({page})=>{
  await fixture(page);await page.setViewportSize({width:1280,height:600});await page.emulateMedia({reducedMotion:'reduce'});await page.goto('/settings');
  await page.getByRole('button',{name:'Light workspace',exact:true}).click();await expect(page.locator('.operational')).toHaveAttribute('data-theme','light');
  await page.getByRole('link',{name:'Explore',exact:true}).click();await expect(page.locator('.operational')).toHaveAttribute('data-theme','light');
  await page.screenshot({path:'../tmp/v3/screenshots/explore-light-short.png',fullPage:true});
  await page.evaluate(()=>{document.documentElement.style.zoom='1.25';});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:'../tmp/v3/screenshots/explore-light-zoom125.png',fullPage:true});
  expect(await page.locator('.workspace-content > main').evaluate(el=>getComputedStyle(el).animationDuration)).toBe('1e-05s');
});
test('bounded dense evidence renders a finite list and inspection does not query providers',async({page})=>{
  await fixture(page);const items=Array.from({length:1700},(_,i)=>({event:{...event,externalId:`dense-${i}`,title:`Development fixture ${i}`,latitude:32.24+i/100000,longitude:77.19+i/100000},distanceKm:i/100}));let reads=0;
  await page.route('**/api/v1/hazards/nearby**',route=>{reads++;return route.fulfill({json:{...nearby,items}});});
  await page.goto('/map');const start=Date.now();await page.getByRole('button',{name:'Manali',exact:true}).click();await expect(page.locator('.event-card')).toHaveCount(50);
  await page.getByRole('button',{name:'Show more evidence',exact:true}).click();await expect(page.locator('.event-card')).toHaveCount(100);expect(reads).toBe(1);
  const markers=await page.locator('.hazard-marker,.hazard-cluster').count();expect(markers).toBeLessThanOrEqual(300);
  mkdirSync('../tmp/v3',{recursive:true});writeFileSync('../tmp/v3/dense-evidence.json',JSON.stringify({scope:'Synthetic 1700-event fixture; local installed Chrome; not a before/after benchmark',elapsedMs:Date.now()-start,renderedCards:100,markers,nearbyRequests:reads,browser:page.context().browser()?.version()},null,2));
});
test('snapshot charts preserve incomplete evidence and original record counts',async({page})=>{
  await fixture(page);
  await page.route('**/api/v1/history/earthquakes**',route=>route.fulfill({json:{id,modelVersion:'fixture',query:{latitude:0,longitude:0,radiusKm:100,from:'2026-01-01T00:00:00Z',to:date,minMagnitude:4},fetchedAt:date,eventCount:2,exposureYears:.8,annualFrequency:null,truncated:true,events:[{...event,measurements:{magnitude:4.2}},{...event,externalId:'other',measurements:{magnitude:5.3}}],source,explanation:'Synthetic incomplete response, not a forecast.'}}));
  await page.goto('/history');await page.getByLabel('Saved snapshot ID').fill(id);await page.getByRole('button',{name:'Load snapshot',exact:true}).click();
  await expect(page.getByRole('img',{name:/<4: 0; 4–<5: 1; 5–<6: 1/})).toBeVisible();await expect(page.getByText('Unavailable — incomplete catalog response')).toBeVisible();
  await page.getByRole('button', { name: 'Show event map' }).click();
  await expect(page.getByLabel('Map of earthquake catalog locations in the retrieved snapshot')).toBeVisible();
  await page.getByText('Stored event evidence (2)', { exact: true }).click();
  const buttons = page.getByRole('button', { name: /Locate Development fixture/ });
  await buttons.first().focus();
  await page.keyboard.press('Enter');
  await expect(buttons.first()).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('region',{name:'Historical result'}).screenshot({path:'../tmp/v3/screenshots/history-result.png'});
});
