'use client';

import { createContext, useContext, useState, type Dispatch, type ReactNode, type SetStateAction } from 'react';
import type { Intelligence } from '../../types/intelligence';
import type { Place } from '../../types/planning';

export type PlaceDraft = { text: string; place: Place | null };
type RouteDraft = { origin: PlaceDraft; destination: PlaceDraft; corridor: string; days: string; magnitude: string };
type InsightDraft = {
  view: 'timeline' | 'compare' | 'terrain';
  includeHistory: boolean;
  data: Intelligence | null;
  selected: string | null;
  provider: string;
  hazard: string;
  classification: string;
  region: string;
  from: string;
  to: string;
  timeField: 'issuedAt' | 'updatedAt' | 'retrievedAt';
};
type DraftState<T> = [T, Dispatch<SetStateAction<T>>];
const RouteContext = createContext<DraftState<RouteDraft> | null>(null);
const InsightsContext = createContext<DraftState<InsightDraft> | null>(null);
const defaultRoute: RouteDraft = { origin: { text: '', place: null }, destination: { text: '', place: null }, corridor: '25', days: '7', magnitude: '4' };
const defaultInsights: InsightDraft = { view: 'timeline', includeHistory: false, data: null, selected: null, provider: 'all', hazard: 'all', classification: 'all', region: '', from: '', to: '', timeField: 'issuedAt' };

export function NavigationDrafts({ owner, children }: { owner: string | null; children: ReactNode }) {
  const route = useState(defaultRoute);
  const insights = useState(defaultInsights);
  const [previousOwner, setPreviousOwner] = useState(owner);
  if (owner !== previousOwner) {
    setPreviousOwner(owner);
    route[1](defaultRoute);
    insights[1](defaultInsights);
  }

  return <RouteContext.Provider value={route}><InsightsContext.Provider value={insights}>{children}</InsightsContext.Provider></RouteContext.Provider>;
}

export function useRouteDraft() {
  const draft = useContext(RouteContext);
  if (!draft) throw new Error('NavigationDrafts is required.');
  return draft;
}

export function useInsightDraft() {
  const draft = useContext(InsightsContext);
  if (!draft) throw new Error('NavigationDrafts is required.');
  return draft;
}
