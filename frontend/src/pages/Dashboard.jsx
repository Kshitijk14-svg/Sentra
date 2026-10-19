import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { ErrorNote, StateBadge, Tape } from '../components.jsx';
import { timeAgo, usePolling } from '../format.js';

const INTERVALS = [
  { value: 10, label: '10 seconds' },
  { value: 30, label: '30 seconds' },
  { value: 60, label: '1 minute' },
  { value: 300, label: '5 minutes' },
];

function AddCheck({ onCreated, onCancel }) {
  const [name, setName] = useState('');
  const [target, setTarget] = useState('');
  const [intervalSeconds, setIntervalSeconds] = useState(60);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.createCheck({ name, target, intervalSeconds: Number(intervalSeconds) });
      setName('');
      setTarget('');
      onCreated();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="add" onSubmit={submit}>
      <label className="field">
        <span>Name</span>
        <input required value={name} onChange={(e) => setName(e.target.value)} placeholder="Marketing site" />
      </label>
      <label className="field field-wide">
        <span>URL to check</span>
        <input required type="url" value={target} onChange={(e) => setTarget(e.target.value)}
          placeholder="https://example.com/health" />
      </label>
      <label className="field">
        <span>Check every</span>
        <select value={intervalSeconds} onChange={(e) => setIntervalSeconds(e.target.value)}>
          {INTERVALS.map((i) => <option key={i.value} value={i.value}>{i.label}</option>)}
        </select>
      </label>
      <div className="add-actions">
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Adding…' : 'Add check'}</button>
        {onCancel && <button type="button" className="btn btn-quiet" onClick={onCancel}>Cancel</button>}
      </div>
      <ErrorNote error={error} />
    </form>
  );
}

export default function Dashboard() {
  const [adding, setAdding] = useState(false);
  const { data, error, refresh } = usePolling(async () => {
    const checks = await api.checks();
    const runs = await Promise.all(checks.map((c) => api.runs(c.id).catch(() => [])));
    return checks.map((check, i) => ({ check, runs: runs[i] }));
  }, 8000);

  const items = data || [];
  const down = items.filter((i) => i.check.state === 'DOWN');
  const loading = data === null && !error;

  let headline = 'Add your first check';
  if (items.length) {
    headline = down.length
      ? `${down.length} of ${items.length} ${items.length === 1 ? 'check is' : 'checks are'} down`
      : items.length === 1 ? 'Your check is up' : `All ${items.length} checks are up`;
  }

  return (
    <>
      <div className="page-head">
        <h1 className={down.length ? 'headline headline-down' : 'headline'}>
          {loading ? 'Loading checks' : headline}
        </h1>
        {items.length > 0 && !adding && (
          <button className="btn btn-primary" onClick={() => setAdding(true)}>Add check</button>
        )}
      </div>

      {error && data === null && <ErrorNote error={error} />}

      {(adding || (!loading && items.length === 0 && !error)) && (
        <AddCheck
          onCreated={() => { setAdding(false); refresh(); }}
          onCancel={items.length ? () => setAdding(false) : undefined}
        />
      )}

      {items.length > 0 && (
        <ul className="rows">
          {items.map(({ check, runs }) => (
            <li key={check.id}>
              <Link to={`/checks/${check.id}`} className="row">
                <div className="row-main">
                  <div className="row-name">{check.name}</div>
                  <div className="row-target">{check.target}</div>
                </div>
                <div className="row-tape"><Tape runs={runs} slots={30} /></div>
                <div className="row-state">
                  <StateBadge state={check.state} />
                  <div className="muted small">
                    {runs.length
                      ? `${runs[0].latencyMs} ms, ${timeAgo(runs[0].startedAt)}`
                      : `every ${check.intervalSeconds}s`}
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
