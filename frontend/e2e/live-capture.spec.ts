import {test,expect} from '@playwright/test';
import {mkdirSync,writeFileSync} from 'node:fs';
test.skip(process.env.CUSTERRA_V3_LIVE_CAPTURE!=='true','Explicit bounded local API/provider acceptance only');
test('capture actual V3 public evidence and a provider route',async({page})=>{
 test.setTimeout(100000);await page.setViewportSize({width:1440,height:1000});mkdirSync('public/product',{recursive:true});
 await page.goto('/map');await page.getByRole('button',{name:'San Francisco',exact:true}).click();await expect(page.getByRole('button',{name:/M [0-9].*CA/}).first()).toBeVisible({timeout:35000});
 await page.getByRole('button',{name:/M [0-9].*CA/}).first().click();await expect(page.getByRole('tabpanel',{name:'Selection'})).toContainText('USGS');
 await expect(page.getByText('Retrieving source alerts…',{exact:true})).toHaveCount(0,{timeout:35000});await page.locator('.explore-search').evaluate(el=>{el.scrollTop=0;});await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:'public/product/v3-explorer.jpg',quality:86});
 await page.goto('/routes');
 for(const [label,name] of [['From','Berlin'],['To','Potsdam']]){await page.getByLabel(label,{exact:true}).fill(name);await page.getByRole('button',{name:`Search ${label.toLowerCase()}`,exact:true}).click();await page.getByRole('group',{name:`Choose ${label.toLowerCase()}`,exact:true}).getByRole('radio',{name:new RegExp(`${name},.*Germany`)}).check();await page.waitForTimeout(1100);}
 await page.getByRole('button',{name:'Analyze route',exact:true}).click();await expect(page.getByRole('region',{name:'Driving route result'})).toBeVisible({timeout:35000});
 await page.getByRole('region',{name:'Driving route planner'}).screenshot({path:'public/product/v3-route.jpg',quality:86});
 writeFileSync('../tmp/v3/continuation/live-capture.json',JSON.stringify({at:new Date().toISOString(),scope:'Actual local V3 app; public San Francisco and Berlin/Potsdam queries; no personal account',browser:page.context().browser()?.version(),map:'OpenStreetMap normal online tiles, no tile prefetch',note:'Still screenshots, not live feeds or staging/production acceptance'},null,2));
});
test('capture the actual bounded source timeline',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await page.goto('/insights');await page.getByRole('button',{name:'Load available feeds'}).click();await expect(page.locator('.intelligence-row').first()).toBeVisible({timeout:35000});await page.locator('.intelligence-row .event-title').first().click();await page.screenshot({path:'public/product/v3-timeline.jpg',quality:86});
});
