import { expect, test } from 'vitest';
import { readCopies, saveCopy, removeCopy, STORAGE_KEY, MAX_AGE_MS } from '../public/offline-store.js';
const now = Date.now();
function storage() { const values = new Map<string, string>(); return { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value); }, removeItem: (key: string) => { values.delete(key); } }; }
function copy(index = 1, server = 'http://localhost:8080') { return { version: 1, savedAt: new Date(now).toISOString(), server, journey: { id: `11111111-1111-4111-8111-${String(index).padStart(12, '0')}`, name: 'Saved journey', status: 'ACTIVE', lastAttemptAt: null, lastCheckedAt: null, lastError: null, route: { points: [{ latitude: 0, longitude: 0 }, { latitude: 0, longitude: 1 }], corridorKm: 25, days: 7, minMagnitude: 4 }, assessment: null }, notices: [] }; }
test('offline copies round-trip, replace per server and can be removed independently', () => {
  const store = storage(); saveCopy(store, copy(), now); saveCopy(store, { ...copy(), journey: { ...copy().journey, name: 'Updated' } }, now);
  saveCopy(store, copy(1, 'http://localhost:8081'), now);
  expect(readCopies(store, now)).toHaveLength(2);
  removeCopy(store, 'http://localhost:8080', copy().journey.id);
  expect(readCopies(store, now)[0].server).toBe('http://localhost:8081');
});
test('expired, incompatible and malformed data is not displayed as evidence', () => {
  const store = storage();
  store.setItem(STORAGE_KEY, JSON.stringify([copy(), { ...copy(2), version: 2 }, { ...copy(3), savedAt: new Date(now - MAX_AGE_MS).toISOString() }]));
  expect(readCopies(store, now)).toHaveLength(1);
  for (const value of ['broken', '{}', '[null]']) { store.setItem(STORAGE_KEY, value); expect(readCopies(store, now)).toEqual([]); }
  expect(() => saveCopy(store, { ...copy(), journey: { ...copy().journey, route: { ...copy().journey.route, points: [{ latitude: 99, longitude: 0 }] } } }, now)).toThrow();
});
test('limits do not evict other journeys or silently swallow quota failures', () => {
  const store = storage(); for (let i = 1; i <= 5; i++) saveCopy(store, copy(i), now);
  expect(() => saveCopy(store, copy(6), now)).toThrow('Five offline copies');
  expect(readCopies(store, now)).toHaveLength(5);
  const full = { ...storage(), setItem: () => { throw new Error('Quota exceeded'); } };
  expect(() => saveCopy(full, copy(), now)).toThrow('Quota exceeded');
  expect(() => saveCopy(storage(), { ...copy(), journey: { ...copy().journey, name: 'x'.repeat(600000) } }, now)).toThrow('storage limit');
});
