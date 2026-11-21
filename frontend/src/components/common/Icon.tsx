import type { CSSProperties } from 'react';

export type IconName = 'explore' | 'route' | 'journey' | 'bell' | 'history' | 'code' | 'settings' | 'arrow' | 'search' | 'layers' | 'close' | 'sun' | 'moon' | 'user' | 'external' | 'chevron' | 'globe' | 'menu' | 'info' | 'download';
const paths: Record<IconName, React.ReactNode> = {
  explore: <><circle cx="12" cy="12" r="8"/><path d="m15 9-2 4-4 2 2-4 4-2Z"/></>,
  route: <><circle cx="6" cy="5" r="2"/><circle cx="18" cy="19" r="2"/><path d="M8 5h7a4 4 0 0 1 0 8H9a3 3 0 0 0 0 6h7"/></>,
  journey: <><rect x="5" y="6" width="14" height="15" rx="2"/><path d="M9 6V3h6v3M5 11h14M9 15h6M9 18h4"/></>,
  bell: <><path d="M5 16h14l-2-3V9a5 5 0 0 0-10 0v4l-2 3ZM10 20h4"/></>,
  history: <><path d="M4 10a8 8 0 1 1 1 7M4 4v6h6M12 7v5l3 2"/></>,
  code: <><path d="m8 7-5 5 5 5m8-10 5 5-5 5m-3-14-2 18"/></>,
  settings: <><path d="m10 3-.6 2.2-2 .9L5.3 5 3 9l1.7 1.5v3L3 15l2.3 4 2.1-1.1 2 .9L10 21h4l.6-2.2 2-.9 2.1 1.1 2.3-4-1.7-1.5v-3L21 9l-2.3-4-2.1 1.1-2-.9L14 3h-4Z"/><circle cx="12" cy="12" r="3"/></>,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6"/>,
  search: <><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/></>,
  layers: <><path d="m12 3 10 6-10 6L2 9l10-6Zm-9 11 9 5 9-5M3 19l9 4 9-4"/></>,
  close: <path d="m6 6 12 12M6 18 18 6"/>,
  sun: <><circle cx="12" cy="12" r="4"/><path d="M12 1v2m0 18v2M1 12h2m18 0h2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/></>,
  moon: <path d="M20 14A8 8 0 0 1 10 4a8 8 0 1 0 10 10Z"/>,
  user: <><circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></>,
  external: <path d="M14 3h7v7m0-7L10 14M10 4H4v16h16v-6"/>,
  chevron: <path d="m9 5 7 7-7 7"/>,
  globe: <><circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/></>,
  menu: <path d="M4 6h16M4 12h16M4 18h16"/>,
  info: <><circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/></>,
  download: <path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4"/>,
};
export default function Icon({ name, size = 20, style }: { name: IconName; size?: number; style?: CSSProperties }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" style={style}>{paths[name]}</svg>;
}
