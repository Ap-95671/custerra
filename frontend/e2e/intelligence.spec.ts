import {test,expect} from '@playwright/test';
import {mkdirSync} from 'node:fs';
import {baseFixture,intelligence,alert,at,locationId} from './intelligenceFixture';
test.beforeEach(async({page})=>{await baseFixture(page);mkdirSync('../tmp/v3/screenshots',{recursive:true});});
test('timeline filters preserve source times, inspection treats markup as text and explanation is explicit',async({page})=>{
 let reads=0,explanations=0;
 await page.route('**/api/v1/intelligence/timeline',r=>{reads++;return r.fulfill({json:intelligence});});
 await page.route('**/api/v1/intelligence/explain',r=>{explanations++;expect(r.request().headers()['x-csrf-token']).toBe('fixture-token');return r.fulfill({json:{text:'Fixture evidence summary. Overall risk UNKNOWN.',mode:'DETERMINISTIC_SOURCE_SUMMARY'}});});
 await page.goto('/insights');await page.getByRole('button',{name:'Load available feeds'}).click();
 await page.getByRole('button',{name:alert.title}).click();await page.getByText('Unaltered source description').click();
 await expect(page.getByText(alert.description,{exact:true})).toBeVisible();await expect(page.locator('img[src="x"]')).toHaveCount(0);
 expect(explanations).toBe(0);await page.getByRole('button',{name:'Explain this evidence'}).click();await expect(page.getByText('Fixture evidence summary.',{exact:false})).toBeVisible();expect(explanations).toBe(1);
 await page.getByLabel('From · UTC inclusive').fill('2026-11-05');await expect(page.getByRole('button',{name:alert.title})).toHaveCount(0);
 await page.getByLabel('From · UTC inclusive').fill('2026-11-04');await expect(page.getByRole('button',{name:alert.title})).toBeVisible();expect(reads).toBe(1);
 await page.getByRole('combobox',{name:'Source',exact:true}).selectOption('NWS');
 const navigation=page.getByRole('navigation',{name:'Explore Custerra'});
 await navigation.getByRole('link',{name:'Routes'}).click();
 await navigation.getByRole('link',{name:'Insights'}).click();
 await expect(page.getByLabel('From · UTC inclusive')).toHaveValue('2026-11-04');
 await expect(page.getByRole('combobox',{name:'Source',exact:true})).toHaveValue('NWS');
 await expect(page.getByRole('button',{name:alert.title})).toBeVisible();expect(reads).toBe(1);
 await page.screenshot({path:'../tmp/v3/screenshots/timeline-detail.png',fullPage:true});
});
test('saved places start with monitoring off, retain history, and require a confirmed deletion',async({page})=>{
 let place={id:locationId,name:'Synthetic destination',latitude:1,longitude:1,radiusKm:100,days:7,monitoringOptIn:false,createdAt:at,lastAttemptAt:null};let exists=false;let history:unknown[]=[];const writes:string[]=[];
 await page.route('**/api/v1/locations**',r=>{const path=new URL(r.request().url()).pathname,method=r.request().method();
 if(method!=='GET'){writes.push(method);expect(r.request().headers()['x-csrf-token']).toBe('fixture-token');}
 if(path.endsWith('/configuration'))return r.fulfill({json:{schedulerEnabled:false}});
 if(path.endsWith('/history'))return r.fulfill({json:history});
 if(path.endsWith('/check')){history=[{id:locationId,locationId,checkedAt:at,evidence:intelligence,changes:[]}];return r.fulfill({json:history[0]});}
 if(method==='PATCH'){place={...place,monitoringOptIn:true};return r.fulfill({json:place});}
 if(method==='POST'){exists=true;return r.fulfill({status:201,json:place});}
 if(method==='DELETE'){exists=false;return r.fulfill({status:204});}
 return r.fulfill({json:exists?[place]:[]});});
 await page.setViewportSize({width:390,height:844});await page.goto('/locations');
 await page.getByLabel('Place name').fill(place.name);await page.getByLabel('Latitude',{exact:true}).fill('1');await page.getByLabel('Longitude',{exact:true}).fill('1');await page.getByRole('button',{name:'Save location',exact:true}).click();
 await expect(page.getByText('Monitoring off',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Check saved location now'}).click();await expect(page.getByRole('button',{name:alert.title})).toBeVisible();
 await page.getByRole('button',{name:'Opt in to location monitoring'}).click();await expect(page.getByRole('button',{name:'Disable location monitoring'})).toBeVisible();await expect(page.getByText('Server scheduling:',{exact:false})).toContainText('disabled');
 await page.screenshot({path:'../tmp/v3/screenshots/saved-place-mobile.png',fullPage:true});
 await page.getByRole('button',{name:'Delete saved location',exact:true}).click();expect(writes).not.toContain('DELETE');await page.getByRole('button',{name:'Confirm location deletion'}).click();await expect(page.getByRole('region',{name:'Selected saved location'})).toHaveCount(0);expect(writes).toContain('DELETE');
});
test('comparison uses equal filters and terrain remains explicitly modeled context',async({page})=>{
 await page.route('**/api/v1/intelligence/compare',r=>{const body=r.request().postDataJSON();expect(body.radiusKm).toBe(100);expect(body.destinations).toHaveLength(2);return r.fulfill({json:{collectedAt:at,limitations:['Synthetic comparison, no safety ranking.'],destinations:body.destinations.map((destination:unknown)=>({destination,intelligence,overallRisk:'UNKNOWN',earthquakes:{fetchedAt:at,truncated:false,items:[],sources:[],coverage:'Synthetic incomplete source',coverageStatus:'PARTIAL'}}))}});});
 await page.route('**/api/v1/intelligence/elevation**', r => {
   expect(new URL(r.request().url()).searchParams.get('includeSlope')).toBe('true');
   return r.fulfill({json:{latitude:1,longitude:1,elevationMeters:123,slope:{degrees:2.4,offsetMeters:225},retrievedAt:at,source:'Synthetic DEM fixture',sourceUrl:'https://open-meteo.com/en/docs/elevation-api',limitations:['Modeled terrain is not a flood probability.']}});
 });
 await page.goto('/insights');await page.getByRole('tab',{name:'Compare places'}).click();for(const i of [1,2]){await page.getByLabel(`Destination ${i} name`).fill(`Place ${i}`);await page.getByLabel(`Destination ${i} latitude`).fill(String(i));await page.getByLabel(`Destination ${i} longitude`).fill(String(i));}
 await page.getByRole('button',{name:'Compare source evidence'}).click();await expect(page.getByText('Overall risk: UNKNOWN',{exact:true})).toHaveCount(2);
 await page.getByRole('tab',{name:'Geographic context'}).click();await page.getByLabel('Terrain latitude').fill('1');await page.getByLabel('Terrain longitude').fill('1');await page.getByRole('button',{name:'Retrieve terrain context'}).click();await expect(page.getByRole('heading',{name:'123 m · modeled elevation'})).toBeVisible();
 await expect(page.getByText(/Modeled slope 2.4°/)).toBeVisible();
});
test('mobile evidence panel collapses and selection opens it again without provider requests',async({page})=>{
 await page.setViewportSize({width:360,height:760});await page.goto('/map');await page.getByRole('button',{name:'Manali',exact:true}).click();await expect(page.getByRole('button',{name:alert.title})).toBeVisible();
 await page.getByRole('button',{name:'Collapse evidence panel'}).click();await expect(page.getByRole('tab',{name:'Context',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:alert.title}).click();await expect(page.getByRole('tabpanel',{name:'Selection'})).toContainText(alert.title);await expect(page.getByRole('button',{name:'Collapse evidence panel'})).toHaveAttribute('aria-expanded','true');
});

test('historical comparison and rainfall keep missing results distinct from zero', async ({ page }) => {
  const window = { from: '2024-01-01T00:00:00Z', to: '2026-01-01T00:00:00Z', minMagnitude: 4 };
  await page.route('**/api/v1/intelligence/compare', r => {
    const request = r.request().postDataJSON();
    expect(request.history).toEqual(window);
    return r.fulfill({ json: { collectedAt: at, limitations: [], historicalWindow: window,
      destinations: request.destinations.map((destination: unknown, index: number) => ({
        destination, intelligence, overallRisk: 'UNKNOWN',
        earthquakes: { fetchedAt: at, truncated: false, items: [], sources: [], coverage: 'Fixture', coverageStatus: 'PARTIAL' },
        history: { coverageStatus: index ? 'UNAVAILABLE' : 'TRUNCATED', eventCount: index ? null : 2000,
          annualFrequency: null, fetchedAt: index ? null : at, source: index ? null : { attribution: 'USGS', documentationUrl: 'https://earthquake.usgs.gov/' } },
      })),
    } });
  });
  let rainfallReads = 0;
  await page.route('**/api/v1/intelligence/rainfall?**', r => {
    rainfallReads++;
    return r.fulfill({ json: { days: Array.from({ length: 7 }, (_, index) => ({ date: `2026-10-2${index}`, rainMm: index === 2 ? null : 0 })),
      totalRainMm: null, incomplete: true, gridLatitude: 1, gridLongitude: 1, retrievedAt: at, source: 'Open-Meteo / ECMWF ERA5',
      sourceUrl: 'https://open-meteo.com/en/docs/historical-weather-api' } });
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/insights');
  await page.getByRole('tab', { name: 'Timeline' }).focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('tab', { name: 'Compare places' })).toBeFocused();
  for (const index of [1, 2]) {
    await page.getByLabel(`Destination ${index} name`).fill(`Place ${index}`);
    await page.getByLabel(`Destination ${index} latitude`).fill(String(index));
    await page.getByLabel(`Destination ${index} longitude`).fill(String(index));
  }
  await page.getByRole('checkbox', { name: 'Include a historical USGS comparison' }).check();
  await page.getByLabel('Comparison history from · inclusive').fill('2024-01-01');
  await page.getByLabel('Comparison history to · exclusive').fill('2026-01-01');
  await page.getByRole('button', { name: 'Compare source evidence' }).click();
  await expect(page.getByText('At least 2000 catalog records')).toBeVisible();
  await expect(page.getByText('Observed frequency withheld due to incomplete results.')).toBeVisible();
  await expect(page.getByText('Historical coverage unavailable.')).toBeVisible();
  await expect(page.getByText(/Historical window, UTC/)).toBeVisible();
  await page.getByRole('tab', { name: 'Geographic context' }).click();
  await expect.poll(() => rainfallReads).toBe(0);
  await page.getByLabel('Terrain latitude').fill('1');
  await page.getByLabel('Terrain longitude').fill('1');
  await page.getByRole('button', { name: 'Retrieve rainfall history' }).click();
  const report = page.getByRole('region', { name: 'Historical rainfall context' });
  await expect(report.getByText('Window total unavailable', { exact: false })).toBeVisible();
  await expect(report.getByText('Unavailable', { exact: true })).toHaveCount(1);
  await expect(report.getByText('0.0 mm', { exact: true })).toHaveCount(6);
  await expect.poll(() => rainfallReads).toBe(1);
});

test('explicit linked revisions and reported positions stay inspectable as distinct records', async ({ page }) => {
  const revision = { ...alert, id: 'fixture-update', title: 'Synthetic flood report update', messageType: 'Update', references: [alert.id] };
  const track = { ...alert, id: 'fixture-track', source: 'EONET', title: 'Synthetic observed storm positions',
    informationType: 'CURATED_EVENT', geometry: { type: 'Point', coordinates: [-179, 2] },
    track: [{ at, latitude: 1, longitude: 179 }, { at, latitude: 2, longitude: -179 }],
  };
  await page.route('**/api/v1/intelligence/timeline', route => route.fulfill({
    json: { ...intelligence, items: [...intelligence.items, { event: revision, state: 'UPDATED', spatial: null }, { event: track, state: 'REPORTED_OPEN', spatial: null }],
      correlations: [{ sourceIdentities: ['NWS:fixture-alert', 'NWS:fixture-update'], basis: 'EXPLICIT_SOURCE_UPDATE_REFERENCE' }],
    },
  }));
  await page.goto('/insights');
  await page.getByRole('button', { name: 'Load available feeds' }).click();
  await page.getByText('Linked source revisions (1)').click();
  await page.locator('.source-revisions').getByRole('button', { name: revision.title }).click();
  await expect(page.getByRole('heading', { name: revision.title })).toBeVisible();
  await page.getByRole('button', { name: track.title }).click();
  await expect(page.locator('.reported-position-track')).toHaveCount(1);
  await expect(page.getByText(/The dashed line connects reported positions/)).toBeVisible();
});
