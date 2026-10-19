import { useState } from 'react';
import { api } from '../api.js';
import { ErrorNote } from '../components.jsx';

// A fixed pattern for the decorative tape: mostly steady, with two failures and a slow patch.
const DEMO = Array.from({ length: 64 }, (_, i) => {
  const fail = i === 22 || i === 23 || i === 24;
  const h = fail ? 100 : 34 + ((i * 37) % 31) + (i > 40 && i < 46 ? 28 : 0);
  return { fail, h };
});

export default function Login({ onSignIn }) {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const registering = mode === 'register';

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = registering ? await api.register(email, password) : await api.login(email, password);
      onSignIn(res.token);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <div className="auth">
      <section className="auth-story">
        <div className="auth-brand">Sentra</div>
        <div>
          <div className="demo-tape" aria-hidden="true">
            {DEMO.map((b, i) => (
              <i key={i} className={b.fail ? 'bar bar-fail' : 'bar bar-ok'}
                style={{ height: `${b.h}%`, '--i': i }} />
            ))}
          </div>
          <h1 className="auth-title">Every check leaves a mark. Failures stand out.</h1>
          <p className="auth-copy">
            Sentra checks your URLs on a schedule and keeps the history. An incident opens only after
            three failures in a row, so one bad response doesn’t page anyone, and it closes after two
            clean runs.
          </p>
        </div>
      </section>

      <section className="auth-form-wrap">
        <form className="auth-form" onSubmit={submit}>
          <h2>{registering ? 'Create your account' : 'Sign in'}</h2>
          <label className="field">
            <span>Email</span>
            <input type="email" autoComplete="email" required value={email}
              onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" required minLength={registering ? 8 : undefined}
              maxLength={72} autoComplete={registering ? 'new-password' : 'current-password'}
              value={password} onChange={(e) => setPassword(e.target.value)} />
            {registering && <small className="muted">At least 8 characters.</small>}
          </label>
          <ErrorNote error={error} />
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Working…' : registering ? 'Create account' : 'Sign in'}
          </button>
          <p className="muted switch">
            {registering ? 'Already have an account?' : 'New to Sentra?'}{' '}
            <button type="button" className="link"
              onClick={() => { setMode(registering ? 'login' : 'register'); setError(null); }}>
              {registering ? 'Sign in' : 'Create an account'}
            </button>
          </p>
        </form>
      </section>
    </div>
  );
}
