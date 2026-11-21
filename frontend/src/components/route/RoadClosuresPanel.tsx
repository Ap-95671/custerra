'use client';

import { useEffect, useRef, useState } from 'react';
import { z } from 'zod';
import { api } from '../../lib/api/journeyApi';
import { formatTimestamp } from '../../lib/utils/dateUtils';
import { safeSourceUrl } from '../../types/hazard';
import type { Journey } from '../../types/journey';

const closureReport = z.object({
  coverage: z.enum(['OUTSIDE_SCOPE', 'UNAVAILABLE', 'STALE', 'PARTIAL']),
  retrievedAt: z.string().nullable(),
  sourceUpdatedAt: z.string().nullable(),
  rejectedRecords: z.number().int().nonnegative(),
  truncated: z.boolean(),
  sourceUrl: z.string(),
  items: z.array(z.object({
    id: z.string(), reportedRoad: z.string(), direction: z.string(), facility: z.string(),
    type: z.string(), work: z.string(), state: z.enum(['ACTIVE', 'SCHEDULED', 'UNCONFIRMED']),
    scheduledStart: z.string(), scheduledEnd: z.string().nullable(), sourceUpdatedAt: z.string(),
    endpoint: z.enum(['BEGIN', 'END']), endpointName: z.string(),
    segmentIndex: z.number().int().nonnegative(), distanceKm: z.number().nonnegative(),
  })).max(30),
});
const states = { ACTIVE: 'Reported started', SCHEDULED: 'Scheduled', UNCONFIRMED: 'Start unconfirmed' };
const coverageMessages = {
  OUTSIDE_SCOPE: 'This route is outside the area checked for the Caltrans District 4 feed.',
  UNAVAILABLE: 'The closure feed could not be checked. Try again or open Caltrans QuickMap.',
  STALE: 'The closure feed is more than 30 minutes old. Check Caltrans QuickMap for current information.',
  PARTIAL: 'District 4 records only. Check Caltrans QuickMap for current reports and exact affected roads.',
};
type Route = { geometry: string } | { points: Journey['route']['points'] };

export default function RoadClosuresPanel({ route, onSelectSegment }: { route: Route; onSelectSegment: (index: number) => void }) {
  const [report, setReport] = useState<z.infer<typeof closureReport> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);

  async function check() {
    const request = new AbortController();
    active.current = request;
    setReport(null);
    setBusy(true);
    setError('');
    try {
      const response = await api('intelligence/road-closures', closureReport, 'POST', route, request.signal);
      if (!request.signal.aborted) setReport(response);
    } catch (cause) {
      if (!request.signal.aborted) setError(cause instanceof Error ? cause.message : 'The closure feed could not be checked.');
    } finally {
      if (!request.signal.aborted) setBusy(false);
    }
  }

  return <section className="feature-card" aria-label="Caltrans closure reports">
    <h3>Bay Area road and lane reports</h3>
    <p>Check Caltrans District 4 for reported closure endpoints within 500 m of the route. A nearby endpoint may be on a different road, lane, ramp or direction.</p>
    <div className="feature-actions">
      <button type="button" disabled={busy} onClick={() => void check()}>Check Caltrans closure reports</button>
      <a className="button" href={safeSourceUrl(report?.sourceUrl ?? 'https://quickmap.dot.ca.gov/')} target="_blank" rel="noreferrer">Open Caltrans QuickMap ↗</a>
    </div>
    {busy && <p role="status">Retrieving reported road and lane closures…</p>}
    {error && <p role="alert">{error}</p>}
    {report && <>
      <p role="status">{coverageMessages[report.coverage]}</p>
      {report.sourceUpdatedAt && <p>Source updated {formatTimestamp(report.sourceUpdatedAt)}{report.retrievedAt && ` · retrieved ${formatTimestamp(report.retrievedAt)}`}</p>}
      {(report.truncated || report.rejectedRecords > 0) && <p>Some records were excluded by validation or result limits.</p>}
      {report.coverage === 'PARTIAL' && report.items.length === 0 && <p>No reported endpoints matched this search. Check local sources for current road conditions.</p>}
      {report.items.length > 0 && <ul>
        {report.items.map(item => <li key={item.id}>
          <strong>{item.reportedRoad} · {item.type}</strong> · {states[item.state]} · {item.facility}{item.direction ? ` · ${item.direction}` : ''}
          {item.work && <div>{item.work}</div>}
          <div>{item.endpoint === 'BEGIN' ? 'Reported start' : 'Reported end'}{item.endpointName ? ` · ${item.endpointName}` : ''} · {Math.round(item.distanceKm * 1000)} m from route segment {item.segmentIndex + 1}</div>
          <div>Scheduled {formatTimestamp(item.scheduledStart)}{item.scheduledEnd ? ` to ${formatTimestamp(item.scheduledEnd)}` : ' · no scheduled end'}. Source updated {formatTimestamp(item.sourceUpdatedAt)}.</div>
          <button type="button" onClick={() => onSelectSegment(item.segmentIndex)}>Focus near {item.reportedRoad} on map</button>
        </li>)}
      </ul>}
    </>}
  </section>;
}
