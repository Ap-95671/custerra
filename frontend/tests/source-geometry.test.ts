import {test,expect} from 'vitest';
import {sourceGeometry} from '../src/lib/maps/sourceGeometry';
test('display geometry wraps across the date line without changing source evidence',()=>{
  const coordinates=[[[179,1],[-179,1],[-179,2],[179,1]]];
  const original=structuredClone(coordinates);
  const displayed=sourceGeometry({type:'Polygon',coordinates},180);
  expect(displayed.coordinates).toEqual([[[179,1],[181,1],[181,2],[179,1]]]);
  expect(coordinates).toEqual(original);
  expect(sourceGeometry({type:'Point',coordinates:[-179,0]},181).coordinates).toEqual([181,0]);
});
test('malformed or oversized source geometry cannot enter map layers',()=>{
  for(const coordinates of [[0,91],[NaN,0],[181,0],['1',2],null])expect(()=>sourceGeometry({type:'Point',coordinates},0)).toThrow();
  expect(()=>sourceGeometry({type:'LineString',coordinates:Array(20001).fill([0,0])},0)).toThrow();
  expect(()=>sourceGeometry({type:'MultiPolygon',coordinates:Array(201).fill([])},0)).toThrow();
});
