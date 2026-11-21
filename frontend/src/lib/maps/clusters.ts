import type { HazardItem } from '../../types/hazard';
export type MarkerGroup = { items: HazardItem[]; selected: boolean };
/** Screen-space grouping is display-only. All source identities and original evidence remain intact. */
export function groupMarkers(items: HazardItem[], project: (item: HazardItem) => {x:number;y:number}, selected: string|null, cell = 48): MarkerGroup[] {
  const groups = new Map<string,MarkerGroup>(); const pinned: MarkerGroup[]=[];
  for (const item of items) {
    if (Math.abs(item.event.latitude)>85) continue;
    if (`${item.event.source}:${item.event.externalId}`===selected) { pinned.push({items:[item],selected:true});continue; }
    const point=project(item);const key=items.length>80?`${Math.floor(point.x/cell)}:${Math.floor(point.y/cell)}`:`${item.event.source}:${item.event.externalId}`;
    const existing=groups.get(key);if(existing)existing.items.push(item);else groups.set(key,{items:[item],selected:false});
  }
  return [...pinned,...groups.values()];
}
