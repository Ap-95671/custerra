import { z } from 'zod';
export const coordinatesSchema = z.object({ latitude: z.number().min(-90).max(90), longitude: z.number().min(-180).max(180) });
export const weatherSchema = z.object({ query: coordinatesSchema, gridLatitude: z.number(), gridLongitude: z.number(),
  informationType: z.literal('MODELED_WEATHER'), validAt: z.string().datetime({ offset: true }), fetchedAt: z.string().datetime({ offset: true }),
  intervalSeconds: z.number().int().min(1).max(3600), temperatureC: z.number().nullable(), rainMm: z.number().nonnegative().nullable(),
  precipitationMm: z.number().nonnegative().nullable(), windSpeedKmh: z.number().nonnegative().nullable(), partial: z.boolean(), stale: z.boolean(),
  source: z.object({ id: z.string(), attribution: z.string(), documentationUrl: z.string(), licensingUrl: z.string(), refreshPolicy: z.string() }),
  license: z.string(), limitations: z.array(z.string()),
});
export type Weather = z.infer<typeof weatherSchema>;
