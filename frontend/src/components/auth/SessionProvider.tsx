"use client";
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { readSession, SESSION_EXPIRED, type Identity } from '../../lib/api/session';
const Context = createContext<{ user: Identity | null; loading: boolean; error: string; refresh: () => Promise<void> }>({ user: null, loading: true, error: '', refresh: async () => {} });
export function SessionProvider({ children }: { children: ReactNode }) {
  const generation = useRef(0);
  const [user, setUser] = useState<Identity | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  async function refresh() { const version = ++generation.current; setLoading(true); setError(''); try { const identity = await readSession(); if (generation.current === version) setUser(identity); } catch { if (generation.current === version) { setUser(null); setError('Account service unavailable. Retry when connected.'); } } finally { if (generation.current === version) setLoading(false); } }
  useEffect(() => {
    const controller = new AbortController(); const version = ++generation.current;
    readSession(controller.signal).then(identity => { if (!controller.signal.aborted && generation.current === version) setUser(identity); }).catch(() => { if (!controller.signal.aborted && generation.current === version) setError('Account service unavailable. Retry when connected.'); }).finally(() => { if (!controller.signal.aborted && generation.current === version) setLoading(false); });
    const expired = () => { generation.current++; setLoading(false); setUser(null); setError('Your session has ended. Sign in again to access saved data.'); };
    window.addEventListener(SESSION_EXPIRED, expired);
    return () => { controller.abort(); window.removeEventListener(SESSION_EXPIRED, expired); };
  }, []);
  return <Context.Provider value={{ user, loading, error, refresh }}>{children}</Context.Provider>;
}
export function useSession() { return useContext(Context); }
export function SessionGate({ children }: { children: ReactNode }) {
  const session = useSession();
  if (session.loading) return <p role="status">Checking your session…</p>;
  if (!session.user) return <section className="feature-card"><h2>Keep your saved evidence private.</h2><p>{session.error || 'Sign in to save and monitor journeys or retrieve your history snapshots.'}</p><a href="/login">Sign in</a> · <a href="/register">Create an account</a>{session.error && <button onClick={() => void session.refresh()}>Retry account connection</button>}</section>;
  return children;
}
