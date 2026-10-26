import type { NextConfig } from 'next';
const config: NextConfig = { output: 'standalone', poweredByHeader: false, async headers() { return [{ source: '/journey-sw.js', headers: [{ key: 'Cache-Control', value: 'no-cache' }] }]; } };
export default config;
