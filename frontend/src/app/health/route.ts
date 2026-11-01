import { webReadiness } from '../../lib/server/webReadiness';
export const dynamic = 'force-dynamic';
export async function GET() { return webReadiness(); }
