import { useCallback, useEffect, useRef, useState } from 'react';
import { getNearby, getRisk, getWeather } from '../lib/api/apiClient';
import type { Weather } from '../types/weather';
import type { Query, Nearby, Risk } from '../types/hazard';
export type Result<T> = { data?: T; error?: string; loading: boolean };
export function useLocationAssessment() {
  const active = useRef<AbortController | null>(null);
  const [query, setQuery] = useState<Query | null>(null);
  const [hazards, setHazards] = useState<Result<Nearby>>({ loading: false });
  const [risk, setRisk] = useState<Result<Risk>>({ loading: false });
  const [weather, setWeather] = useState<Result<Weather>>({ loading: false });
  useEffect(() => () => active.current?.abort(), []);
  const load = useCallback((next: Query) => {
    active.current?.abort();
    const controller = new AbortController(); active.current = controller;
    setQuery(next); setHazards({ loading: true }); setRisk({ loading: true }); setWeather({ loading: true });
    getNearby(next, controller.signal).then(data => { if (!controller.signal.aborted) setHazards({ data, loading: false }); })
      .catch(error => { if (!controller.signal.aborted) setHazards({ error: error.message, loading: false }); });
    getWeather(next, controller.signal).then(data => { if (!controller.signal.aborted) setWeather({ data, loading: false }); })
      .catch(error => { if (!controller.signal.aborted) setWeather({ error: error.message, loading: false }); });
    getRisk(next, controller.signal).then(data => { if (!controller.signal.aborted) setRisk({ data, loading: false }); })
      .catch(error => { if (!controller.signal.aborted) setRisk({ error: error.message, loading: false }); });
  }, []);
  return { query, hazards, risk, weather, load };
}
