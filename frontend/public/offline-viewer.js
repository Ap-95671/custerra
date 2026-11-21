import { readCopies, removeCopy, STORAGE_KEY, MAX_BYTES } from './offline-store.js';
import { plotRoute } from './offline-geometry.js';
const container = document.getElementById('copies');
const message = document.getElementById('message');
function add(parent, tag, value) { const node = document.createElement(tag); node.textContent = value; parent.append(node); return node; }
function evidence(parent, match) {
  const line = add(parent, 'p', `${match.event.title} · ${match.event.source} · ${match.event.hazardType || 'EARTHQUAKE'} · ${match.event.informationType || 'OBSERVED_EVENT'} · ${match.event.startedAt} · ${match.distanceKm.toFixed(1)} km from segment ${match.segmentIndex + 1}`);
  try { const url = new URL(match.event.sourceUrl); if (url.protocol === 'https:' && !url.username && !url.password) { const a = add(line, 'a', ' Source (requires connection)'); a.href = url.href; a.rel = 'noreferrer'; } } catch { /* Invalid evidence links are not navigable. */ }
}

function drawRoute(parent, points) {
  const canvas = document.createElement('canvas');
  canvas.width = 900;
  canvas.height = 240;
  canvas.className = 'route-sketch';
  canvas.setAttribute('role', 'img');
  canvas.setAttribute('aria-label', 'Saved route shape from start to end. Coordinate list follows.');
  const context = canvas.getContext('2d');
  if (!context) return;
  const projected = plotRoute(points, canvas.width, canvas.height, 24);
  context.strokeStyle = '#86b9aa';
  context.lineWidth = 3.5;
  context.lineJoin = 'round';
  context.beginPath();
  context.moveTo(...projected[0]);
  for (const point of projected.slice(1)) context.lineTo(...point);
  context.stroke();
  for (const [label, point] of [['Start', projected[0]], ['End', projected.at(-1)]]) {
    const [x, y] = point;
    context.beginPath();
    context.arc(x, y, 4, 0, Math.PI * 2);
    context.fillStyle = label === 'Start' ? '#a4cbbc' : '#eda478';
    context.fill();
  }
  parent.append(canvas);
  add(parent, 'p', 'Saved route shape · green start, orange end. Geographic basemap and surrounding hazards require a connection.');
}

function render() {
  container.replaceChildren();
  try {
    const copies = readCopies(localStorage);
    const bytes=(localStorage.getItem(STORAGE_KEY)?.length ?? 0)*2;
    document.getElementById('connection').textContent=(navigator.onLine?'Online connection reported — this page still shows saved evidence only.':'Offline — live monitoring and new observations are unavailable.')+` Storage: ${(bytes/1024).toFixed(0)} / ${MAX_BYTES/1024} KiB.`;
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
      if(journey.generatedGeometry)add(card,'p',`${journey.generatedGeometry.originName} → ${journey.generatedGeometry.destinationName}`);
      if(Date.now()-Date.parse(copy.savedAt)>86400000)add(card,'p','This copy is over 24 hours old. Refresh it online before relying on its timestamps.');
      add(card, 'h3', `Saved geometry · ${journey.route.points.length} vertices`);
      drawRoute(card, journey.route.points);
      const coordinateList = add(card, 'details', '');
      add(coordinateList, 'summary', 'Read saved coordinates (first 100)');
      const points = add(coordinateList, 'ol', '');
      for (const point of journey.route.points.slice(0,100)) add(points, 'li', `${point.latitude}, ${point.longitude}`);
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
      if(copy.areaEvidence){
        const saved=copy.areaEvidence;add(card,'h3','Saved official/curated source-area evidence');
        if(saved.error)add(card,'p',`Last area-check status: ${saved.error}. Earlier evidence is retained with its original time.`);
        const areas=saved.assessment;
        if(areas){add(card,'p',`Assessed ${areas.assessedAt} · coverage ${areas.coverageStatus} · from original segment ${(saved.fromSegment ?? 0)+1}. Source geometry intersections do not establish physical impact.`);
          for(const item of areas.items){const e=item.event;add(card,'p',`${e.title} · ${e.source} · ${e.informationType} · state at saved assessment: ${item.state}. Issued ${e.issuedAt}; retrieved ${e.retrievedAt}.`);add(card,'p',`Original segments ${item.spatial.segmentIndices.map(i=>i+1).join(', ')||'Unassessed'} · length within source polygon ${item.spatial.lengthWithinKm===null?'Not assessed':item.spatial.lengthWithinKm.toFixed(2)+' km'}.`);if(e.expiresAt)add(card,'p',`Source expiry/closure: ${e.expiresAt}. This offline reader does not obtain new cancellations or updates.`);if(e.description)add(card,'p',e.description);try{const url=new URL(e.sourceUrl);if(url.protocol==='https:'&&!url.username&&!url.password){const a=add(card,'a','Original source (requires connection)');a.href=url.href;a.rel='noreferrer';}}catch{/* Invalid source links stay inert. */}}
          if(!areas.items.length)add(card,'p','No intersecting items in this saved response. Missing source geometry and incomplete coverage remain possible.');
        }else add(card,'p','No source-area assessment was saved.');
      }
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
window.addEventListener('online',render);window.addEventListener('offline',render);
render();
// Purge expired copies even if this reader remains open for a long time.
setInterval(render, 60000);
