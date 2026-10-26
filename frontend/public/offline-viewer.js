import { readCopies, removeCopy, STORAGE_KEY } from './offline-store.js';
const container = document.getElementById('copies');
const message = document.getElementById('message');
function add(parent, tag, value) { const node = document.createElement(tag); node.textContent = value; parent.append(node); return node; }
function evidence(parent, match) {
  const line = add(parent, 'p', `${match.event.title} · ${match.event.source} · ${match.event.hazardType || 'EARTHQUAKE'} · ${match.event.informationType || 'OBSERVED_EVENT'} · ${match.event.startedAt} · ${match.distanceKm.toFixed(1)} km from segment ${match.segmentIndex + 1}`);
  try { const url = new URL(match.event.sourceUrl); if (url.protocol === 'https:' && !url.username && !url.password) { const a = add(line, 'a', ' Source (requires connection)'); a.href = url.href; a.rel = 'noreferrer'; } } catch { /* Invalid evidence links are not navigable. */ }
}
function render() {
  container.replaceChildren();
  try {
    const copies = readCopies(localStorage);
    message.textContent = copies.length ? `${copies.length} saved copies. All evidence may be out of date.` : 'No valid offline copies. Expired or incompatible copies are removed. Save a journey while online before departure.';
    document.getElementById('clear').disabled = !copies.length;
    for (const copy of copies) {
      const journey = copy.journey; const assessment = journey.assessment;
      const card = add(container, 'section', ''); add(card, 'h2', journey.name);
      add(card, 'p', `Saved ${copy.savedAt} · expires ${new Date(Date.parse(copy.savedAt) + 7 * 86400000).toISOString()}`);
      add(card, 'p', `Source server: ${copy.server} · status when saved: ${journey.status}`);
      add(card, 'p', `Last complete check: ${journey.lastCheckedAt || 'No baseline yet'} · last attempt: ${journey.lastAttemptAt || 'Never'}`);
      for (const checkpoint of journey.providerCheckpoints || []) add(card, 'p', `${checkpoint.provider} last successful evidence check: ${checkpoint.lastSuccessfulCheck}`);
      if (journey.lastError) add(card, 'p', `Last saved check error: ${journey.lastError}. This copy does not resolve it.`);
      add(card, 'p', `Corridor ${journey.route.corridorKm} km · magnitude ≥ ${journey.route.minMagnitude} · lookback ${journey.route.days} days`);
      add(card, 'h3', 'Saved waypoints'); const points = add(card, 'ol', '');
      for (const point of journey.route.points) add(points, 'li', `${point.latitude}, ${point.longitude}`);
      add(card, 'h3', 'Saved route assessment · UNKNOWN');
      if (assessment) {
        add(card, 'p', `Assessed ${assessment.assessedAt} · ${assessment.lengthKm.toFixed(1)} km`);
        add(card, 'p', `Saved data coverage: ${assessment.coverageStatus || 'UNKNOWN'}`);
        for (const source of assessment.providerStatuses || []) add(card, 'p', `${source.provider}: ${source.coverageStatus} · ${source.limitations.join(' · ')}`);
        if (assessment.truncated) add(card, 'p', 'Incomplete catalog coverage: matching observations may be missing.');
        add(card, 'p', assessment.explanation);
        for (const match of assessment.matches) evidence(card, match);
        if (!assessment.matches.length) add(card, 'p', 'No matching observations in this saved assessment. This does not establish safety.');
      } else add(card, 'p', 'No assessment was available when this copy was saved.');
      add(card, 'h3', 'Saved informational notices (latest 50 at most)');
      for (const notice of copy.notices) { evidence(card, notice.evidence); add(card, 'p', `${notice.createdAt} · ${notice.type || 'Informational notice'} · ${notice.explanation}`); }
      if (!copy.notices.length) add(card, 'p', 'No notices in this copy.');
      const remove = add(card, 'button', 'Remove this offline copy'); remove.type = 'button';
      remove.onclick = () => { try { removeCopy(localStorage, copy.server, journey.id); render(); } catch { message.textContent = 'Could not remove the copy. Check browser storage permissions.'; } };
    }
  } catch { message.textContent = 'Browser storage is unavailable. Offline copies cannot be read.'; }
}
document.getElementById('clear').onclick = () => { try { localStorage.removeItem(STORAGE_KEY); render(); } catch { message.textContent = 'Could not clear offline copies. Check browser storage permissions.'; } };
window.addEventListener('storage', render);
render();
// Purge expired copies even if this reader remains open for a long time.
setInterval(render, 60000);
