'use client';
import {useState} from 'react';
import dynamic from 'next/dynamic';
import type {Journey} from '../../types/journey';
import type {RouteExposure} from '../../types/intelligence';
import AreaExposurePanel from './AreaExposurePanel';
import RoadClosuresPanel from './RoadClosuresPanel';
const SavedRouteMap=dynamic(()=>import('./SavedRouteMap'),{ssr:false});
export default function JourneyAreaMap({journey}:{journey:Journey}){const [areas,setAreas]=useState<RouteExposure|null>(null);const [segment,setSegment]=useState<number|null>(null);return <><SavedRouteMap journey={journey} areaExposure={areas} selectedSegment={segment}/><AreaExposurePanel journeyId={journey.id} points={journey.route.points} onChange={setAreas} onSelectSegment={setSegment}/><RoadClosuresPanel route={journey.generatedGeometry ? { geometry: journey.generatedGeometry.encodedPolyline } : { points: journey.route.points }} onSelectSegment={setSegment}/></>;}
