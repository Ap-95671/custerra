import type {Page} from '@playwright/test';
import {authFixture} from './authFixture';
export const at='2026-11-04T01:00:00Z';
export const locationId='11111111-1111-4111-8111-111111111111';
export const alert={id:'fixture-alert',source:'NWS',hazardType:'FLOOD',informationType:'OFFICIAL_WARNING',title:'SYNTHETIC DEVELOPMENT — flood warning',description:'<img src=x onerror=alert(1)> Unaltered fixture source text.',severity:'Severe',urgency:'Immediate',certainty:'Observed',language:'en-US',authority:'NWS development fixture',messageType:'Alert',status:'CURRENT',issuedAt:at,effectiveAt:at,expiresAt:'2026-11-05T01:00:00Z',updatedAt:at,retrievedAt:at,areaDescription:'Synthetic California test area',sourceUrl:'https://api.weather.gov/alerts/fixture',geometry:{type:'Polygon',coordinates:[[[0,0],[2,0],[2,2],[0,2],[0,0]]]},references:[],sourceLinks:[{id:'NWS',url:'https://api.weather.gov/alerts/fixture'}],track:[],attributes:{instruction:'Synthetic test — no action required.'}};
export const sourceStatus={source:'NWS',retrievedAt:at,coverageStatus:'PARTIAL',limitations:['Synthetic fixture. California coverage only.']};
export const intelligence={retrievedAt:at,coverageStatus:'PARTIAL',items:[{event:alert,state:'CURRENT',spatial:{intersects:true,distanceKm:0,method:'SYNTHETIC'}}],sources:[sourceStatus],correlations:[],limitations:['Synthetic fixture, not a live warning.']};
export const areas={assessedAt:at,overallRisk:'UNKNOWN',coverageStatus:'PARTIAL',truncated:false,sources:[sourceStatus],items:[{event:alert,state:'CURRENT',spatial:{intersects:true,lengthWithinKm:12.5,segmentIndices:[0],method:'SYNTHETIC'}}],limitations:['Synthetic source geometry intersection, not physical impact.']};
export const savedArea={journeyId:locationId,lastAttemptAt:at,fromSegment:0,assessment:areas,error:null};
export async function baseFixture(page:Page){
 await page.route('https://tile.openstreetmap.org/**',r=>r.abort());
 await page.route('**/api/v1/**',r=>{const auth=authFixture(r);if(auth)return auth;const path=new URL(r.request().url()).pathname;
 if(path.includes('/intelligence/nearby')||path.includes('/intelligence/timeline'))return r.fulfill({json:intelligence});
 if(path.endsWith('/intelligence/route'))return r.fulfill({json:areas});
 if(path.endsWith('/areas'))return r.fulfill({json:savedArea});
 if(path.endsWith('/monitoring'))return r.fulfill({json:{schedulerEnabled:false,intervalMs:300000}});
 return r.fulfill({json:[]});});
}
