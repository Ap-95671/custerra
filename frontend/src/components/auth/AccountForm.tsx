"use client";
import { useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { authenticate } from '../../lib/api/session';
import { useSession } from './SessionProvider';
import FeatureNav from '../layout/FeatureNav';
export default function AccountForm({ register = false }: { register?: boolean }) {
  const [error,setError]=useState('');const [busy,setBusy]=useState(false);const session=useSession();const router=useRouter();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();setBusy(true);setError('');const form=event.currentTarget;const data=new FormData(form);
    try { await authenticate(String(data.get('username')),String(data.get('password')),register);form.reset();await session.refresh();router.replace('/routes'); }
    catch(e) { setError(e instanceof Error ? e.message : 'Account request failed.'); }
    finally { setBusy(false); }
  }
  return <div className="app-shell"><FeatureNav/><main className="feature-page"><h1>{register ? 'Create your Custerra account' : 'Sign in to Custerra'}</h1><p>Save private journeys and historical evidence. Public hazard exploration remains available without an account.</p>
    <form className="feature-card" onSubmit={submit}><fieldset disabled={busy}><legend>{register ? 'Account details' : 'Your account'}</legend>
      <label>Username<input name="username" required minLength={3} maxLength={40} pattern="[a-zA-Z0-9_]+" autoComplete="username" autoCapitalize="none"/></label>
      <label>Password<input name="password" type="password" required minLength={register ? 12 : 1} maxLength={72} autoComplete={register ? 'new-password' : 'current-password'}/></label>
      {register && <p>Use 3–40 letters, digits or underscores for your username. Passwords need at least 12 characters and at most 72 UTF-8 bytes. Account recovery is not available yet; keep your password in a password manager.</p>}
      <button>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</button></fieldset></form>
    {error && <p role="alert">{error}</p>}<a href={register ? '/login' : '/register'}>{register ? 'Already have an account? Sign in' : 'Create an account'}</a></main></div>;
}
