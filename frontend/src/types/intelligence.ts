import { z } from 'zod';
const time=z.string().datetime({offset:true});
export const shapeSchema=z.object({type:z.enum(['Point','LineString','Polygon','MultiPolygon']),coordinates:z.unknown()});
export const intelligenceEventSchema=z.object({id:z.string(),source:z.enum(['NWS','EONET']),hazardType:z.string(),informationType:z.string(),title:z.string(),description:z.string(),severity:z.string(),urgency:z.string(),certainty:z.string(),language:z.string(),authority:z.string(),messageType:z.string(),status:z.string(),issuedAt:time,effectiveAt:time.nullable(),expiresAt:time.nullable(),updatedAt:time,retrievedAt:time,areaDescription:z.string(),sourceUrl:z.string(),geometry:shapeSchema.nullable(),references:z.array(z.string()),sourceLinks:z.array(z.object({id:z.string(),url:z.string()})),track:z.array(z.object({at:time,latitude:z.number(),longitude:z.number()})),attributes:z.record(z.string(),z.string())});
export const intelligenceItemSchema=z.object({event:intelligenceEventSchema,state:z.string(),spatial:z.object({intersects:z.boolean().nullable(),distanceKm:z.number().nullable(),method:z.string()}).nullable()});
const sourceSchema=z.object({source:z.string(),retrievedAt:time.nullable(),coverageStatus:z.string(),limitations:z.array(z.string())});
export const intelligenceSchema=z.object({retrievedAt:time,coverageStatus:z.string(),items:z.array(intelligenceItemSchema).max(700),sources:z.array(sourceSchema),correlations:z.array(z.object({sourceIdentities:z.array(z.string()),basis:z.string()})),limitations:z.array(z.string())});
export type Intelligence=z.infer<typeof intelligenceSchema>;
export type IntelligenceItem=z.infer<typeof intelligenceItemSchema>;
export const routeExposureSchema=z.object({assessedAt:time,overallRisk:z.literal('UNKNOWN'),coverageStatus:z.string(),truncated:z.boolean(),sources:z.array(sourceSchema),items:z.array(z.object({event:intelligenceEventSchema,state:z.string(),spatial:z.object({intersects:z.boolean().nullable(),lengthWithinKm:z.number().nullable(),segmentIndices:z.array(z.number().int().nonnegative()),method:z.string()})})),limitations:z.array(z.string())});
export type RouteExposure=z.infer<typeof routeExposureSchema>;
export const savedLocationSchema=z.object({id:z.string().uuid(),name:z.string(),latitude:z.number(),longitude:z.number(),radiusKm:z.number(),days:z.number(),monitoringOptIn:z.boolean(),createdAt:time,lastAttemptAt:time.nullable()});
export const locationCheckSchema=z.object({id:z.string().uuid(),locationId:z.string().uuid(),checkedAt:time,evidence:intelligenceSchema,changes:z.array(z.object({source:z.string(),eventId:z.string(),type:z.string()}))});

export const savedAreasSchema=z.object({journeyId:z.string().uuid(),lastAttemptAt:time.nullable(),fromSegment:z.number().int().nonnegative().nullable(),assessment:routeExposureSchema.nullable(),error:z.string().nullable()});
export type SavedAreas=z.infer<typeof savedAreasSchema>;
