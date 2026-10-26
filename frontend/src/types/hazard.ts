import { z } from 'zod';
export const querySchema = z.object({ latitude: z.number().min(-90).max(90), longitude: z.number().min(-180).max(180), radiusKm: z.number().min(1).max(1000), days: z.number().int().min(1).max(30) });
export type Query = z.infer<typeof querySchema>;
const timestamp = z.string().datetime({ offset: true });
const source = z.object({ id: z.string(), attribution: z.string(), documentationUrl: z.string(), licensingUrl: z.string(), refreshPolicy: z.string() });
const level = z.enum(['UNKNOWN', 'LOW', 'MODERATE', 'HIGH']);
export const nearbySchema = z.object({
  fetchedAt: timestamp, truncated: z.boolean(), coverage: z.string(), sources: z.array(source),
  coverageStatus: z.enum(['COMPLETE', 'PARTIAL', 'TRUNCATED', 'UNAVAILABLE', 'UNKNOWN']).optional(),
  providerStatuses: z.array(z.object({ provider: z.string(), coverageStatus: z.enum(['COMPLETE', 'PARTIAL', 'TRUNCATED', 'UNAVAILABLE', 'UNKNOWN']),
    retrievedAt: timestamp.nullable(), limitations: z.array(z.string()) })).optional(),
  items: z.array(z.object({ distanceKm: z.number().nonnegative(), event: z.object({
    externalId: z.string(), source: z.string(), title: z.string(), hazardType: z.string(), informationType: z.string(),
    latitude: z.number().min(-90).max(90), longitude: z.number().min(-180).max(180), sourceUrl: z.string(),
    observedAt: timestamp.nullable().optional(), sourceUpdatedAt: timestamp.nullable().optional(), retrievedAt: timestamp.nullable().optional(),
    confidence: z.string().optional(), attributes: z.record(z.string(), z.string()).nullable().optional(),
    startedAt: timestamp, severity: z.string(), measurements: z.record(z.string(), z.number()),
  }) })),
});
export const riskSchema = z.object({
  modelVersion: z.string(), assessedAt: timestamp, overallRisk: level, score: z.number().nullable(), confidence: z.string(),
  explanation: z.string(), safetyNotice: z.string(), sourceResultsTruncated: z.boolean(), dataSources: z.array(source),
  dominantHazards: z.array(z.string()).optional(), assessedHazardCount: z.number().optional(), unassessedHazardCount: z.number().optional(),
  coverageStatus: z.string().optional(), weatherStatus: z.string().optional(),
  providerStatuses: nearbySchema.shape.providerStatuses,
  hazards: z.array(z.object({ hazardType: z.string(), scope: z.string(), modelVersion: z.string().nullable(),
    riskLevel: level, score: z.number().min(0).max(100).nullable(), confidence: z.string(), explanation: z.string(), limitations: z.array(z.string()),
    evidence: z.array(nearbySchema.shape.items.element).optional(),
    environmentalContext: z.array(z.object({ code: z.string(), value: z.number().nullable(), unit: z.string(), source: z.string(), validAt: timestamp, retrievedAt: timestamp, explanation: z.string() })).optional(),
    factors: z.array(z.object({ externalId: z.string(), source: z.string(), sourceUrl: z.string(), explanation: z.string(),
      magnitude: z.number().nullable(), distanceKm: z.number(), ageHours: z.number(), contribution: z.number().nullable() })),
  })),
});
export type Nearby = z.infer<typeof nearbySchema>;
export type HazardItem = Nearby['items'][number];
export type Risk = z.infer<typeof riskSchema>;
export function safeSourceUrl(value: string): string | undefined {
  try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password ? url.href : undefined; } catch { return undefined; }
}
