import { z } from 'zod';
import { nearbySchema } from './hazard';
const time = z.string().datetime({ offset: true });
export const pointSchema = z.object({ latitude: z.number().min(-90).max(90), longitude: z.number().min(-180).max(180) });
export const routeInputSchema = z.object({ points: z.array(pointSchema).min(2).max(20), corridorKm: z.number().min(1).max(100), days: z.number().int().min(1).max(30), minMagnitude: z.number().min(0).max(10) });
export const evidenceSchema = z.object({ externalId: z.string(), source: z.string(), title: z.string(), sourceUrl: z.string(), startedAt: time, latitude: z.number(), longitude: z.number(), measurements: z.record(z.string(), z.number()), hazardType: z.string().optional(), informationType: z.string().optional(), severity: z.string().optional(), confidence: z.string().optional(), observedAt: time.nullable().optional(), retrievedAt: time.nullable().optional(), sourceUpdatedAt: time.nullable().optional(), endedAt: time.nullable().optional(), affectedRadiusKm: z.number().nullable().optional(), attributes: z.record(z.string(), z.string()).optional() });
const matchSchema = z.object({ event: evidenceSchema, distanceKm: z.number().nonnegative(), segmentIndex: z.number().int().nonnegative() });
export const assessmentSchema = z.object({ modelVersion: z.string(), query: routeInputSchema, assessedAt: time, lengthKm: z.number(), overallRisk: z.literal('UNKNOWN'), truncated: z.boolean(), matches: z.array(matchSchema), explanation: z.string(), coverageStatus: nearbySchema.shape.coverageStatus, providerStatuses: nearbySchema.shape.providerStatuses, sources: nearbySchema.shape.sources.optional() });
export const journeySchema = z.object({ id: z.string().uuid(), name: z.string(), status: z.enum(['ACTIVE', 'PAUSED']), route: routeInputSchema, createdAt: time, lastAttemptAt: time.nullable(), lastCheckedAt: time.nullable(), lastError: z.string().nullable(), assessment: assessmentSchema.nullable(), providerCheckpoints: z.array(z.object({ provider: z.string(), lastSuccessfulCheck: time })).optional() });
export const noticesSchema = z.array(z.object({ id: z.string().uuid(), journeyId: z.string().uuid(), createdAt: time, type: z.string(), evidence: matchSchema, stateVersion: z.number().optional(), explanation: z.string() }));
export type Assessment = z.infer<typeof assessmentSchema>;
export type Journey = z.infer<typeof journeySchema>;
export type Notice = z.infer<typeof noticesSchema>[number];
export function parsePoints(text: string) {
  return text.trim().split(/\n/).map(line => {
    const parts = line.split(',').map(part => part.trim());
    if (parts.length !== 2 || parts.some(part => part === '')) throw new Error('Use one latitude, longitude pair per line.');
    return pointSchema.parse({ latitude: Number(parts[0]), longitude: Number(parts[1]) });
  });
}
