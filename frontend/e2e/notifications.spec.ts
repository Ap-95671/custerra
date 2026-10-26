import { test, expect } from '@playwright/test';
import { authFixture } from './authFixture';
const id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';const time='2026-10-11T12:00:00Z';
const event={externalId:'fixture',source:'GDACS',title:'Fixture flood advisory',sourceUrl:'https://www.gdacs.org/',startedAt:time,latitude:0,longitude:0,measurements:{},hazardType:'FLOOD',informationType:'ADVISORY',severity:'HIGH',confidence:'UNKNOWN'};
const item={id,journeyName:'Fixture journey',notice:{id,journeyId:id,createdAt:time,type:'UPDATED_MATCHING_EVIDENCE',stateVersion:2,evidence:{event,distanceKm:2,segmentIndex:1},explanation:'Fixture change. Informational, not an official warning.'},readAt:null as string|null,deliveries:[{channel:'WEB_PUSH',status:'UNCERTAIN',attempts:1,lastError:'DELIVERY_OUTCOME_UNKNOWN'}]};
test('notification inbox preserves source evidence, uncertain delivery, preferences and read state on mobile',async({page})=>{
  let current={...item};let preference={enabled:true,pushEnabled:false,hazardTypes:['FLOOD'],minimumSeverity:'UNKNOWN',includeUnknown:true};
  await page.route('**/api/v1/**',route=>{
    const auth=authFixture(route);if(auth)return auth;
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/preferences')) {if(route.request().method()==='PATCH')preference=route.request().postDataJSON();return route.fulfill({json:preference});}
    if(path.endsWith('/push'))return route.fulfill({json:{configured:false,dispatcherEnabled:false,publicKey:null}});
    if(path.endsWith('/read')){current={...current,readAt:time};return route.fulfill({status:204});}
    if(path.endsWith(id))return route.fulfill({json:current});
    return route.fulfill({json:[current]});
  });
  await page.setViewportSize({width:390,height:844});await page.goto('/alerts');
  const inbox=page.getByRole('region',{name:'Notification inbox'});
  await expect(inbox.getByText('WEB_PUSH: UNCERTAIN',{exact:false})).toBeVisible();
  await expect(inbox.getByRole('link',{name:'Fixture flood advisory'})).toHaveAttribute('href','https://www.gdacs.org/');
  await expect(page.getByRole('button',{name:'Enable this browser'})).toBeDisabled();
  await page.getByLabel('Include evidence with UNKNOWN severity').uncheck();
  await page.getByRole('button',{name:'Save preferences'}).click();
  await expect(page.getByText('Notification preferences saved.',{exact:false})).toBeVisible();expect(preference.includeUnknown).toBe(false);
  await inbox.getByRole('button',{name:'Mark read'}).click();
  await expect(inbox.getByText('READ · UPDATED_MATCHING_EVIDENCE',{exact:false})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('push link loads its specific owned notice and explains unavailable setup',async({page})=>{
  await page.route('**/api/v1/**',route=>{
    const auth=authFixture(route);if(auth)return auth;const path=new URL(route.request().url()).pathname;
    return route.fulfill({json:path.endsWith('/preferences')?{enabled:true,pushEnabled:false,hazardTypes:['FLOOD'],minimumSeverity:'UNKNOWN',includeUnknown:true}:path.endsWith('/push')?{configured:false,dispatcherEnabled:false,publicKey:null}:path.endsWith(id)?item:[]});
  });
  await page.goto(`/alerts?notice=${id}`);
  await expect(page.getByRole('region',{name:'Selected notification'}).getByRole('heading',{name:'Fixture journey'})).toBeVisible();
  await expect(page.getByText('Web push is unavailable',{exact:false})).toBeVisible();
  await expect(page.getByRole('region',{name:'Notification inbox'}).getByText('No notifications on this page.',{exact:false})).toBeVisible();
});
test('service worker requests display once and keeps persistent identity deduplication',async({page,context})=>{
  await context.grantPermissions(['notifications']);
  const cdp=await context.newCDPSession(page);
  let registrationId='';
  cdp.on('ServiceWorker.workerRegistrationUpdated',event=>{
    const registration=event.registrations.find((r:{scopeURL:string;isDeleted:boolean})=>r.scopeURL==='http://127.0.0.1:3000/'&&!r.isDeleted);
    if(registration)registrationId=registration.registrationId;
  });
  await cdp.send('ServiceWorker.enable');await page.goto('/offline.html');
  await page.evaluate(async()=>{await navigator.serviceWorker.register('/journey-sw.js');await navigator.serviceWorker.ready;});
  await expect.poll(()=>registrationId).not.toBe('');
  const send=(messageId:string)=>cdp.send('ServiceWorker.deliverPushMessage',{origin:'http://127.0.0.1:3000',registrationId,data:JSON.stringify({id:messageId,title:'Fixture Custerra notice',body:'Test evidence only',url:'https://untrusted.example/'})});
  // This headless build reports notification permission denied inside workers even with an override.
  // Stub only the native display boundary; exercise real push events and IndexedDB deduplication.
  const worker=context.serviceWorkers()[0];
  await worker.evaluate(()=>{
    const scope=globalThis as unknown as {displayed:string[]};scope.displayed=[];
    ServiceWorkerRegistration.prototype.showNotification=async(_title,options)=>{scope.displayed.push(options?.data.id);};
  });
  const shown=()=>worker.evaluate(()=>(globalThis as unknown as {displayed:string[]}).displayed);
  await send(id);await expect.poll(shown).toEqual([id]);
  await worker.evaluate(()=>{(globalThis as unknown as {displayed:string[]}).displayed=[];});
  await send(id);const second='cccccccc-cccc-4ccc-8ccc-cccccccccccc';await send(second);
  await expect.poll(shown).toEqual([second]);
});
