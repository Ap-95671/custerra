"use client";
import { useState } from 'react';
import { useSession } from '../auth/SessionProvider';
import { endSession } from '../../lib/api/session';
import { STORAGE_KEY } from '../../../public/offline-store.js';
export default function FeatureNav() {
  const session=useSession(); const [error,setError]=useState(''); const [busy,setBusy]=useState(false);
  async function logout() { setBusy(true);setError('');try { await endSession();await session.refresh();try { localStorage.removeItem(STORAGE_KEY); } catch { setError('Signed out, but offline copies could not be removed. Clear them on the Offline copies page.'); } } catch { setError('Sign-out failed. Please retry.'); } finally { setBusy(false); } }
  return <><nav className="feature-nav" aria-label="Explore Custerra"><a href="/">Location explorer</a><a href="/history">Historical exposure</a><a href="/routes">Journeys & monitoring</a><a href="/alerts">Notifications</a><a href="/offline.html">Offline copies</a>
    {session.user ? <><span>Signed in as {session.user.username}</span><button disabled={busy} onClick={() => void logout()}>Sign out</button></> : <a href="/login">Sign in</a>}</nav>{error && <p role="alert">{error}</p>}</>;
}
