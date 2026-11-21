import type { IntelligenceItem } from '../../types/intelligence';
/** Display unwrapping only. Source coordinates in evidence remain unchanged. */
export function sourceGeometry(shape:NonNullable<IntelligenceItem['event']['geometry']>,anchor:number) {
  let count=0;
  function coordinate(value:unknown):number[]{if(!Array.isArray(value)||value.length<2||value.length>3||!value.slice(0,2).every(v=>typeof v==='number'&&Number.isFinite(v))||Math.abs(value[0])>180||Math.abs(value[1])>90||++count>20000)throw new Error('Invalid source geometry');return [value[0],value[1]];}
  function line(value:unknown):number[][]{if(!Array.isArray(value)||value.length>20000)throw new Error('Invalid source geometry');let previous=anchor;return value.map(raw=>{const p=coordinate(raw);while(p[0]-previous>180)p[0]-=360;while(p[0]-previous< -180)p[0]+=360;previous=p[0];return p;});}
  function polygon(value:unknown):number[][][]{if(!Array.isArray(value)||value.length>200)throw new Error('Invalid source geometry');return value.map(line);}
  const raw=shape.coordinates;
  const coordinates=shape.type==='Point'?line([raw])[0]:shape.type==='LineString'?line(raw):shape.type==='Polygon'?polygon(raw):Array.isArray(raw)&&raw.length<=200?raw.map(polygon):null;
  if(!coordinates)throw new Error('Invalid source geometry');return {type:shape.type,coordinates};
}

/** Connect only past points reported by the same source record. */
export function reportedPositions(track: IntelligenceItem['event']['track'], anchor: number) {
  if (track.length < 2) return null;
  return sourceGeometry({
    type: 'LineString',
    coordinates: track.map(point => [point.longitude, point.latitude]),
  }, anchor);
}
