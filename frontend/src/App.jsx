import { useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { clearToken, emailFromToken, getToken, setToken } from './api.js';
import { BrandMark } from './components.jsx';
import Login from './pages/Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import CheckDetail from './pages/CheckDetail.jsx';

export default function App() {
  const [token, setTokenState] = useState(getToken());
  const navigate = useNavigate();

  useEffect(() => {
    // api.js fires this when any call comes back 401, so an expired token lands on the sign-in page.
    const onLogout = () => setTokenState(null);
    window.addEventListener('sentra:logout', onLogout);
    return () => window.removeEventListener('sentra:logout', onLogout);
  }, []);

  const signIn = (t) => {
    setToken(t);
    setTokenState(t);
    navigate('/');
  };
  const signOut = () => {
    clearToken();
    setTokenState(null);
    navigate('/login');
  };

  if (!token) {
    return (
      <Routes>
        <Route path="/login" element={<Login onSignIn={signIn} />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">
          <BrandMark />
          Sentra
          <span className="brand-tag">Monitoring</span>
        </Link>
        <div className="topbar-user">
          <span className="muted">{emailFromToken(token)}</span>
          <span className="avatar" aria-hidden="true">{emailFromToken(token).charAt(0) || '?'}</span>
          <button className="btn btn-quiet" onClick={signOut}>Sign out</button>
        </div>
      </header>
      <main className="page">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/checks/:id" element={<CheckDetail />} />
          <Route path="/login" element={<Navigate to="/" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
