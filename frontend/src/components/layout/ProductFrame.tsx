'use client';
import Link from 'next/link';
import Image from 'next/image';
import { usePathname } from 'next/navigation';
import { createContext, useContext, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import { useSession } from '../auth/SessionProvider';
import Icon, { type IconName } from '../common/Icon';
import { NavigationDrafts } from './NavigationDrafts';

const navigation: { href: string; label: string; icon: IconName }[] = [
  { href: '/map', label: 'Explore', icon: 'explore' }, { href: '/routes', label: 'Routes', icon: 'route' },
  { href: '/journeys', label: 'Journeys', icon: 'journey' }, { href: '/alerts', label: 'Alerts', icon: 'bell' },
  { href: '/history', label: 'History', icon: 'history' }, { href: '/insights', label: 'Insights', icon: 'globe' }, { href: '/locations', label: 'Places', icon: 'search' }, { href: '/developer', label: 'Developer', icon: 'code' },
];
const Appearance = createContext<{ theme: string; setTheme: (value: string) => void }>({ theme: 'dark', setTheme: () => {} });
export const useAppearance = () => useContext(Appearance);
let transientTheme = 'dark';
function readAppearance() { try { return localStorage.getItem('custerra.appearance') === 'light' ? 'light' : transientTheme; } catch { return transientTheme; } }
function subscribeAppearance(callback: () => void) { window.addEventListener('custerra-appearance', callback); window.addEventListener('storage', callback); return () => { window.removeEventListener('custerra-appearance', callback); window.removeEventListener('storage', callback); }; }
function subscribeMobile(callback: () => void) { const media=window.matchMedia('(max-width:580px)');media.addEventListener('change',callback);return ()=>media.removeEventListener('change',callback); }
function setTheme(value: string) { transientTheme=value;try { localStorage.setItem('custerra.appearance', value); } catch { /* session-only preference */ } window.dispatchEvent(new Event('custerra-appearance')); }

export default function ProductFrame({ children }: { children: ReactNode }) {
  const path = usePathname(); const session = useSession();
  const theme = useSyncExternalStore(subscribeAppearance, readAppearance, () => 'dark');
  const [menu, setMenu] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const rail = useRef<HTMLElement>(null);
  function dismissMenu() {
    setMenu(false);
    menuButton.current?.focus();
  }
  const mobile = useSyncExternalStore(subscribeMobile, () => window.matchMedia('(max-width:580px)').matches, () => false);
  useEffect(() => {
    if (!menu || !mobile) return;
    rail.current?.querySelector<HTMLAnchorElement>('[aria-current="page"], nav a')?.focus();
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') dismissMenu();
    };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [menu, mobile]);
  const publicPage = path === '/' || path === '/login' || path === '/register';
  const active = navigation.find(item => item.href === path || (item.href === '/map' && path === '/dashboard'));
  return <Appearance.Provider value={{ theme, setTheme }}><div className={publicPage ? 'public-root' : 'operational'} data-theme={theme}>
    <a className="skip-link" href="#main-content">Skip to content</a>
    {publicPage ? children : <>
      <aside ref={rail} id="workspace-navigation" className={`app-rail ${menu ? 'rail-expanded' : ''}`} aria-label="Application navigation" inert={mobile && !menu} aria-hidden={mobile && !menu ? true : undefined}>
        <Link className="rail-brand" href="/" aria-label="Custerra home"><Image src="/brand/custerra-logo.jpg" width={36} height={36} alt="" priority/></Link>
        <nav aria-label="Explore Custerra">{navigation.map(item => <Link href={item.href} key={item.href} className={`rail-link ${active?.href === item.href ? 'is-active' : ''}`} aria-current={active?.href === item.href ? 'page' : undefined} title={item.label} onClick={() => setMenu(false)}><Icon name={item.icon}/><span>{item.label}</span></Link>)}</nav>
        <div className="rail-bottom"><Link className={`rail-link ${path === '/settings' ? 'is-active' : ''}`} href="/settings" aria-label="Settings" title="Settings" aria-current={path === '/settings' ? 'page' : undefined} onClick={() => setMenu(false)}><Icon name="settings"/><span>Settings</span></Link><Link className="rail-link" href="/offline.html" title="Offline copies"><Icon name="download"/><span>Offline</span></Link></div>
      </aside>
      {menu && mobile && <div className="nav-scrim" aria-hidden="true" onPointerDown={dismissMenu}/>}
      <div className="application-body">
        <header className="workspace-header"><div className="workspace-identity"><button ref={menuButton} className="icon-button mobile-menu" onClick={() => setMenu(!menu)} aria-controls="workspace-navigation" aria-label={menu ? 'Close navigation' : 'Open navigation'} aria-expanded={menu}><Icon name={menu ? 'close' : 'menu'}/></button><span className="workspace-wordmark">Custerra<span className="brand-period">.</span></span><span className="header-divider"/><span>{active?.label ?? 'Settings'}</span></div>
          <div className="workspace-account"><span className="workspace-scope">Evidence workspace</span><button className="icon-button" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')} title={`Use ${theme === 'dark' ? 'light' : 'dark'} appearance`} aria-label={`Use ${theme === 'dark' ? 'light' : 'dark'} appearance`}><Icon name={theme === 'dark' ? 'sun' : 'moon'} size={18}/></button><Link href="/alerts" className="icon-button" aria-label="Notifications"><Icon name="bell" size={18}/></Link><Link className="account-link" aria-label={session.user ? `Account: ${session.user.username}` : 'Sign in'} href={session.user ? '/settings' : '/login'}><span className="account-avatar" aria-hidden="true"><Icon name="user" size={15}/></span><span>{session.user?.username ?? 'Sign in'}</span></Link></div>
        </header>
        <div className="workspace-content" id="main-content" tabIndex={-1}>
          <NavigationDrafts owner={session.user?.id ?? null}>{children}</NavigationDrafts>
        </div>
        <div className="workspace-footnote"><Icon name="info" size={13}/><span>Evidence has limits. Overall risk remains UNKNOWN. Follow official local guidance.</span><span className="workspace-version">V3 / PREVIEW</span></div>
      </div>
    </>}
  </div></Appearance.Provider>;
}
