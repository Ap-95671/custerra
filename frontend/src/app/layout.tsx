import { SessionProvider } from '../components/auth/SessionProvider';
import ProductFrame from '../components/layout/ProductFrame';
import { LocationProvider } from '../hooks/useLocationAssessment';
import type { Metadata, Viewport } from 'next';
import 'leaflet/dist/leaflet.css';
import './globals.css';
export const metadata: Metadata = { title: 'Custerra — Hazard intelligence', description: 'Explore earthquake observations and transparent, experimental activity assessments.', icons: { icon: '/brand/custerra-logo.jpg', apple: '/brand/custerra-logo.jpg' } };
export const viewport: Viewport = { width: 'device-width', initialScale: 1, viewportFit: 'cover' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>
    {process.env.NEXT_PUBLIC_DEMO_MODE === 'true' && <aside aria-label="Demo limitations" style={{padding:'0.75rem 1rem',background:'#fff7dc',color:'#5c3b00',textAlign:'center',lineHeight:1.5}}>
      Temporary demo — the app may sleep and saved data may expire. Keep your own copies. Background monitoring and outbound notifications are disabled here.
    </aside>}
    <SessionProvider><LocationProvider><ProductFrame>{children}</ProductFrame></LocationProvider></SessionProvider></body></html>;
}
