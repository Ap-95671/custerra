import { expect, test } from 'vitest';
import { decodeRoute, placesSchema } from '../src/types/planning';
test('polyline6 preserves actual coordinate ordering and rejects corrupt geometry', () => {
  expect(decodeRoute('???_c`|@')).toEqual([[0, 0], [0, 1]]);
  for (const input of ['', '??', '????~', '~~~~~~~??', '??\n?']) expect(() => decodeRoute(input)).toThrow();
});
test('place candidates preserve ambiguity and missing regional context', () => {
  const result = placesSchema.parse({ candidates: [{ id: '1', name: 'Delhi', region: null, country: 'India', location: { latitude: 28.6, longitude: 77.2 } }], retrievedAt: '2026-10-09T12:00:00Z', source: { id: 'test', attribution: 'test', documentationUrl: 'https://example.test', licensingUrl: 'https://example.test', refreshPolicy: 'test' } });
  expect(result.candidates[0].region).toBeNull();
});
