#!/bin/bash
set -euo pipefail
# Bind only Next publicly. Stop both processes when either exits or the host stops us.
export SERVER_ADDRESS=127.0.0.1 SERVER_PORT=8080 BACKEND_INTERNAL_URL=http://127.0.0.1:8080
if [[ -n "${RENDER_EXTERNAL_URL:-}" ]]; then
  export CORS_ALLOWED_ORIGINS="$RENDER_EXTERNAL_URL"
fi
sh /app/start-backend.sh &
api_pid=$!
web_pid=''
cleanup() {
  trap - TERM INT EXIT
  kill -TERM "$api_pid" ${web_pid:+"$web_pid"} 2>/dev/null || true
  wait "$api_pid" ${web_pid:+"$web_pid"} 2>/dev/null || true
}
trap cleanup TERM INT EXIT
# Do not expose the UI until migrations and database readiness have succeeded.
node --input-type=module - <<'JS'
for (let attempt = 0; attempt < 180; attempt++) {
  try {
    const response = await fetch('http://127.0.0.1:8080/actuator/health/readiness', {signal: AbortSignal.timeout(1000)});
    if (response.ok && (await response.json()).status === 'UP') process.exit(0);
  } catch {}
  await new Promise(resolve => setTimeout(resolve, 1000));
}
console.error('Demo backend did not become ready.');
process.exit(1);
JS
node server.js &
web_pid=$!
set +e
wait -n "$api_pid" "$web_pid"
result=$?
exit "$result"
