import { z } from 'zod';
import { evidenceSchema } from './journey';
const time = z.string().datetime({ offset: true });
export const historySchema = z.object({ id: z.string().uuid(), modelVersion: z.string(), query: z.object({ latitude: z.number(), longitude: z.number(), radiusKm: z.number(), from: time, to: time, minMagnitude: z.number() }), fetchedAt: time, eventCount: z.number().int().nonnegative(), exposureYears: z.number().positive(), annualFrequency: z.number().nonnegative().nullable(), truncated: z.boolean(), events: z.array(evidenceSchema), source: z.object({ attribution: z.string(), documentationUrl: z.string() }), explanation: z.string() });
export type History = z.infer<typeof historySchema>;
