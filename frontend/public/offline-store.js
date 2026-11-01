// Shared by the React save action and the standalone offline viewer.
import { packCopy, unpackCopy } from './offline-geometry.js';
export const STORAGE_KEY = 'custerra.journey-copies.v1';
export const MAX_AGE_MS = 7 * 86400000;
export const MAX_BYTES = 1024 * 1024;
const text = value => typeof value === 'string';
const instant = value => text(value) && Number.isFinite(Date.parse(value));
const nullableTime = value => value === null || instant(value);
const point = p => p && Number.isFinite(p.latitude) && Math.abs(p.latitude) <= 90 && Number.isFinite(p.longitude) && Math.abs(p.longitude) <= 180;
const event = e => e && text(e.title) && text(e.source) && text(e.externalId) && text(e.sourceUrl) && instant(e.startedAt);
const checkpoints = value => value === undefined || (Array.isArray(value) && value.length <= 20 && value.every(c => c && text(c.provider) && instant(c.lastSuccessfulCheck)));
const statuses = value => value === undefined || (Array.isArray(value) && value.length <= 20 && value.every(c => c && text(c.provider) && text(c.coverageStatus) && nullableTime(c.retrievedAt) && Array.isArray(c.limitations) && c.limitations.every(text)));
const match = m => m && event(m.event) && Number.isFinite(m.distanceKm) && m.distanceKm >= 0 && Number.isInteger(m.segmentIndex) && m.segmentIndex >= 0;
export function validCopy(copy, now = Date.now()) {
  if (!copy || copy.version !== 1 || !instant(copy.savedAt) || Date.parse(copy.savedAt) > now + 300000 || now - Date.parse(copy.savedAt) >= MAX_AGE_MS) return false;
  try { const url = new URL(copy.server); if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return false; } catch { return false; }
  const j = copy.journey; const r = j?.route; const a = j?.assessment;
  return !!(j && text(j.id) && /^[0-9a-f-]{36}$/i.test(j.id) && text(j.name) && ['PLANNED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED'].includes(j.status)
    && checkpoints(j.providerCheckpoints) && nullableTime(j.lastCheckedAt) && nullableTime(j.lastAttemptAt) && (j.lastError === null || text(j.lastError))
    && r && Array.isArray(r.points) && r.points.length >= 2 && r.points.length <= 20000 && r.points.every(point)
    && Number.isFinite(r.corridorKm) && r.corridorKm >= 1 && r.corridorKm <= 100 && Number.isInteger(r.days) && r.days >= 1 && r.days <= 30
    && Number.isFinite(r.minMagnitude) && r.minMagnitude >= 0 && r.minMagnitude <= 10
    && (a === null || (a && a.overallRisk === 'UNKNOWN' && statuses(a.providerStatuses) && (a.coverageStatus === undefined || text(a.coverageStatus)) && instant(a.assessedAt) && text(a.explanation) && typeof a.truncated === 'boolean'
      && Number.isFinite(a.lengthKm) && Array.isArray(a.matches) && a.matches.length <= 1700 && a.matches.every(match)))
    && Array.isArray(copy.notices) && copy.notices.length <= 50 && copy.notices.every(n => n && n.journeyId === j.id && instant(n.createdAt) && text(n.explanation) && match(n.evidence)));
}
export function readCopies(storage, now = Date.now()) {
  const raw = storage.getItem(STORAGE_KEY);
  if (!raw) return [];
  try {
    if (raw.length * 2 > MAX_BYTES) throw new Error('Oversized storage');
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.length > 5) throw new Error('Invalid storage');
    const valid = parsed.flatMap(copy => { try { const decoded=unpackCopy(copy); return validCopy(decoded,now)?[decoded]:[]; } catch { return []; } });
    if (valid.length !== parsed.length) storage.setItem(STORAGE_KEY, JSON.stringify(valid.map(packCopy)));
    return valid;
  } catch { storage.removeItem(STORAGE_KEY); return []; }
}
export function saveCopy(storage, copy, now = Date.now()) {
  if (!validCopy(copy, now)) throw new Error('Offline copy is invalid or expired. Refresh the journey and try again.');
  const copies = readCopies(storage, now).filter(item => item.server !== copy.server || item.journey.id !== copy.journey.id);
  if (copies.length >= 5) throw new Error('Five offline copies are already saved. Remove one on the Offline copies page first.');
  const payload = JSON.stringify([copy, ...copies].map(packCopy));
  if (payload.length * 2 > MAX_BYTES) throw new Error('Offline storage limit reached. Remove a copy before saving this journey.');
  storage.setItem(STORAGE_KEY, payload);
}
export function removeCopy(storage, server, id) {
  storage.setItem(STORAGE_KEY, JSON.stringify(readCopies(storage).filter(copy => copy.server !== server || copy.journey.id !== id).map(packCopy)));
}
