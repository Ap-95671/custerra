import { journeySchema, noticesSchema, type Journey, type Notice } from '../types/journey';
import { saveCopy, removeCopy } from '../../public/offline-store.js';
export const offlineServer = (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '') || (typeof window !== 'undefined' ? window.location.origin : '');
async function prepareReader() {
  if (!window.isSecureContext || !('serviceWorker' in navigator)) throw new Error('Offline reading needs HTTPS or localhost and service-worker support.');
  await navigator.serviceWorker.register('/journey-sw.js', { scope: '/', updateViaCache: 'none' });
  let timeout: ReturnType<typeof setTimeout> | undefined;
  try {
    await Promise.race([navigator.serviceWorker.ready, new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error('Offline reader is not ready. Stay online and try saving again.')), 15000); })]);
    const cache = await caches.open('custerra-offline-shell-v3');
    // Repair evicted assets even when an existing worker is already active.
    await cache.addAll(['/offline.html', '/offline-viewer.js', '/offline-store.js', '/offline.css']);
  } finally { clearTimeout(timeout); }
}
export async function saveJourneyOffline(journey: Journey, notices: Notice[]) {
  const validJourney = journeySchema.parse(journey); const validNotices = noticesSchema.parse(notices);
  await prepareReader();
  try { saveCopy(localStorage, { version: 1, server: offlineServer, savedAt: new Date().toISOString(), journey: validJourney, notices: validNotices }); }
  catch (error) {
    if (error instanceof DOMException) throw new Error('Browser storage is unavailable or full. The offline copy was not saved.');
    throw error;
  }
}
export function removeJourneyOffline(id: string) {
  removeCopy(localStorage, offlineServer, id);
}
