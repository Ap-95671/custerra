'use client';
import { createContext, createElement, useContext, useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { getNearby, getRisk, getWeather, request } from '../lib/api/apiClient';
import { intelligenceSchema, type Intelligence } from '../types/intelligence';
import type { Weather } from '../types/weather';
import type { Query, Nearby, Risk } from '../types/hazard';
export type Result<T> = { data?: T; error?: string; loading: boolean };
function useLocationState() {
  const active = useRef<AbortController | null>(null);
  const [query, setQuery] = useState<Query | null>(null);
  const [hazards, setHazards] = useState<Result<Nearby>>({ loading: false });
  const [risk, setRisk] = useState<Result<Risk>>({ loading: false });
  const [weather, setWeather] = useState<Result<Weather>>({ loading: false });
  const [intelligence,setIntelligence]=useState<Result<Intelligence>>({loading:false});
  const [intelligenceLayers,setIntelligenceLayers]=useState({NWS:true,EONET:true});
  const [mapViewport, setMapViewport] = useState<{latitude:number;longitude:number;zoom:number}|null>(null);
  const [selected,setSelected]=useState<string|null>(null);
  const [layers,setLayers]=useState({earthquakes:true,thermal:true,global:true});
  useEffect(() => () => active.current?.abort(), []);
  const load = useCallback((next: Query) => {
    active.current?.abort();
    const controller = new AbortController(); active.current = controller;
    setQuery(next); setHazards({ loading: true }); setRisk({ loading: true }); setWeather({ loading: true });setIntelligence({loading:true});
    request('intelligence/nearby',next,intelligenceSchema,controller.signal).then(data=>{if(!controller.signal.aborted)setIntelligence({data,loading:false});}).catch(error=>{if(!controller.signal.aborted)setIntelligence({error:error.message,loading:false});});
    getNearby(next, controller.signal).then(data => { if (!controller.signal.aborted) setHazards({ data, loading: false }); })
      .catch(error => { if (!controller.signal.aborted) setHazards({ error: error.message, loading: false }); });
    getWeather(next, controller.signal).then(data => { if (!controller.signal.aborted) setWeather({ data, loading: false }); })
      .catch(error => { if (!controller.signal.aborted) setWeather({ error: error.message, loading: false }); });
    getRisk(next, controller.signal).then(data => { if (!controller.signal.aborted) setRisk({ data, loading: false }); })
      .catch(error => { if (!controller.signal.aborted) setRisk({ error: error.message, loading: false }); });
  }, []);
  return { query, hazards, risk, weather, intelligence, intelligenceLayers, setIntelligenceLayers, load, mapViewport, setMapViewport, selected, setSelected, layers, setLayers };
}
const LocationContext = createContext<ReturnType<typeof useLocationState> | null>(null);
export function LocationProvider({ children }: { children: ReactNode }) {
  const state = useLocationState();
  return createElement(LocationContext.Provider, { value: state }, children);
}
export function useLocationAssessment() {
  const value = useContext(LocationContext);
  if (!value) throw new Error('LocationProvider is required.');
  return value;
}
