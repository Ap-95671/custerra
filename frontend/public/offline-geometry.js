// Bounded, lossless decoding of the saved provider polyline6; no tile or network access.
export function decodeGeometry(encoded) {
  if(typeof encoded!=='string' || encoded.length>200000)throw new Error('Invalid geometry');
  let index=0,latitude=0,longitude=0;const points=[];
  function number(){let value=0,shift=0;while(true){if(index>=encoded.length || shift>30)throw new Error('Invalid geometry');const digit=encoded.charCodeAt(index++)-63;if(digit<0 || digit>63)throw new Error('Invalid geometry');value+=(digit&31)*2**shift;if(digit<32)return value%2?-(Math.floor(value/2)+1):value/2;shift+=5;}}
  while(index<encoded.length){latitude+=number();longitude+=number();if(Math.abs(latitude)>90000000 || Math.abs(longitude)>180000000 || points.length>=20000)throw new Error('Invalid geometry');points.push({latitude:latitude/1000000,longitude:longitude/1000000});}
  if(points.length<2)throw new Error('Invalid geometry');return points;
}
export function unpackCopy(copy) {
  if(!copy?.journey?.route)return copy;
  const journey={...copy.journey,route:{...copy.journey.route}};
  if(journey.route.geometryEncoding==='polyline6')journey.route.points=decodeGeometry(journey.route.encodedPoints);
  const range=journey.assessment?.query?.pointRange;
  if(range){if(!Number.isInteger(range.start)||!Number.isInteger(range.count)||range.start<0||range.count<2||range.start+range.count>journey.route.points.length)throw new Error('Invalid assessment geometry');journey.assessment={...journey.assessment,query:{...journey.assessment.query,points:journey.route.points.slice(range.start,range.start+range.count)}};}
  return {...copy,journey};
}
export function packCopy(copy) {
  const journey={...copy.journey,progress:null,route:{...copy.journey.route}};
  const points=journey.route.points;
  const encoded=journey.generatedGeometry?.encodedPolyline;
  if(encoded){const decoded=decodeGeometry(encoded);if(decoded.length!==points.length || decoded.some((p,i)=>p.latitude!==points[i].latitude||p.longitude!==points[i].longitude))throw new Error('Saved geometry mismatch');journey.route={...journey.route,points:undefined,geometryEncoding:'polyline6',encodedPoints:encoded};}
  const query=journey.assessment?.query;
  if(query?.points){const start=points.length-query.points.length;if(start>=0 && query.points.every((p,i)=>p.latitude===points[start+i].latitude && p.longitude===points[start+i].longitude))journey.assessment={...journey.assessment,query:{...query,points:undefined,pointRange:{start,count:query.points.length}}};}
  return {...copy,journey};
}
