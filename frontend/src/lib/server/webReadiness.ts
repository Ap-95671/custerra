/** Public readiness stays minimal and includes the API's database health. */
export async function webReadiness() {
  const internal = process.env.BACKEND_INTERNAL_URL
    ?? (process.env.BACKEND_HOST ? `http://${process.env.BACKEND_HOST}:8080` : undefined);
  if (internal) {
    try {
      const response = await fetch(`${internal}/actuator/health/readiness`, {
        cache: 'no-store', signal: AbortSignal.timeout(3000), redirect: 'error',
      });
      if (response.ok && (await response.json()).status === 'UP') {
        return Response.json({ status: 'UP' }, { headers: { 'Cache-Control': 'no-store' } });
      }
    } catch { /* Dependency errors must not expose internal addresses or details. */ }
  }
  return Response.json({ status: 'DOWN' }, { status: 503, headers: { 'Cache-Control': 'no-store' } });
}
