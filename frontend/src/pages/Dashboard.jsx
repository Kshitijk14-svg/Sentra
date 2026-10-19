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
  const [type, setType] = useState('HTTP');
  const [target, setTarget] = useState('');
  const [host, setHost] = useState('');
  const [port, setPort] = useState('');
  const [site, setSite] = useState('');
  const [intervalSeconds, setIntervalSeconds] = useState(60);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const body = { type, name, intervalSeconds: Number(intervalSeconds) };
      if (site.trim()) body.site = site.trim();
      if (type === 'TCP') Object.assign(body, { host, port: Number(port) });
      else body.target = target;
      await api.createCheck(body);
      setName('');
      setTarget('');
      setHost('');
      setPort('');
      setSite('');
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
      <label className="field">
        <span>Type</span>
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="HTTP">Website (HTTP)</option>
          <option value="TCP">Port (TCP)</option>
        </select>
      </label>
      {type === 'TCP' ? (
        <>
          <label className="field field-wide">
            <span>Host</span>
            <input required value={host} onChange={(e) => setHost(e.target.value)} placeholder="db.example.com" />
          </label>
          <label className="field">
            <span>Port</span>
            <input required type="number" min="1" max="65535" value={port}
              onChange={(e) => setPort(e.target.value)} placeholder="5432" />
          </label>
        </>
      ) : (
        <label className="field field-wide">
          <span>URL to check</span>
          <input required type="url" value={target} onChange={(e) => setTarget(e.target.value)}
            placeholder="https://example.com/health" />
        </label>
      )}
      <label className="field">
        <span>Site (optional)</span>
        <input value={site} onChange={(e) => setSite(e.target.value)} placeholder="example.com" />
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

/** One group per site, in first-seen order; checks with no site share a trailing group. */
function bySite(items) {
  const groups = new Map();
  for (const item of items) {
    const key = item.check.site || 'Other';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  return [...groups];
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

      {bySite(items).map(([site, group]) => (
        <section key={site} className="site">
          <h2 className="site-name">{site}</h2>
          <ul className="rows">
          {group.map(({ check, runs }) => (
            <li key={check.id}>
              <Link to={`/checks/${check.id}`} className="row">
                <div className="row-main">
                  <div className="row-name">
                    {check.name} <span className="type-tag">{check.type}</span>
                  </div>
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
        </section>
      ))}
    </>
  );
}
