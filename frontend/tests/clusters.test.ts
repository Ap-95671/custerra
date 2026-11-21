import { test,expect } from 'vitest';
import { groupMarkers } from '../src/lib/maps/clusters';
import type { HazardItem } from '../src/types/hazard';
function fixtures(count:number):HazardItem[] { return Array.from({length:count},(_,index)=>({distanceKm:index,event:{source:'USGS',externalId:String(index),latitude:0,longitude:index/100}} as HazardItem)); }
test('dense grouping preserves every source identity and pins selected evidence outside its cluster',()=>{
  const items=fixtures(1700);const groups=groupMarkers(items,()=>({x:10,y:10}),'USGS:123');
  expect(groups).toHaveLength(2);expect(groups[0].items[0].event.externalId).toBe('123');
  expect(new Set(groups.flatMap(group=>group.items.map(item=>item.event.externalId))).size).toBe(1700);
  expect(items).toHaveLength(1700);
});
test('small evidence sets remain individually selectable; polar evidence remains available off map',()=>{
  const items=fixtures(3);items[1].event.latitude=89;
  expect(groupMarkers(items,()=>({x:0,y:0}),null)).toHaveLength(2);
  expect(items).toHaveLength(3);
});
test('zoom-dependent screen positions separate distant evidence rather than correlating incidents',()=>{
  const items=fixtures(100);
  expect(groupMarkers(items,item=>({x:item.distanceKm*50,y:0}),null)).toHaveLength(100);
  expect(groupMarkers(items,()=>({x:1,y:1}),null)).toHaveLength(1);
});
