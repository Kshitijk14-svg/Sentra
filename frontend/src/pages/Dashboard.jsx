import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { ErrorNote, StateBadge, Tape, TypeTag } from '../components.jsx';
import { STATE, timeAgo, usePolling } from '../format.js';

const INTERVALS = [
  { value: 10, label: '10 seconds' },
  { value: 30, label: '30 seconds' },
  { value: 60, label: '1 minute' },
  { value: 300, label: '5 minutes' },
  { value: 3600, label: '1 hour' },
  { value: 86400, label: '1 day' },
];

const PERIODS = [
  { value: 60, label: '1 minute' },
  { value: 300, label: '5 minutes' },
  { value: 900, label: '15 minutes' },
  { value: 3600, label: '1 hour' },
  { value: 21600, label: '6 hours' },
  { value: 86400, label: '1 day' },
  { value: 604800, label: '1 week' },
];

const TYPES = [
  { value: 'HTTP', label: 'Website', icon: '🌐' },
  { value: 'TCP', label: 'Port', icon: '🔌' },
  { value: 'TLS', label: 'Certificate', icon: '🔒' },
  { value: 'CRON', label: 'Cron job', icon: '⏱' },
];

function AddCheck({ onCreated, onCancel }) {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [type, setType] = useState('HTTP');
  const [target, setTarget] = useState('');
  const [host, setHost] = useState('');
  const [port, setPort] = useState('');
  const [warnDays, setWarnDays] = useState('14');
  const [site, setSite] = useState('');
  const [intervalSeconds, setIntervalSeconds] = useState(60);
  const [periodSeconds, setPeriodSeconds] = useState(3600);
  const [graceMinutes, setGraceMinutes] = useState('5');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      // CRON checks get the backend's own re-check cadence; their schedule is the expected ping period.
      const body = type === 'CRON' ? { type, name } : { type, name, intervalSeconds: Number(intervalSeconds) };
      if (site.trim()) body.site = site.trim();
      if (type === 'TCP') Object.assign(body, { host, port: Number(port) });
      else if (type === 'TLS') {
        Object.assign(body, { host, warnDays: Number(warnDays) });
        if (port) body.port = Number(port);
      }
      else if (type === 'CRON') {
        Object.assign(body, { periodSeconds: Number(periodSeconds), graceSeconds: Number(graceMinutes) * 60 });
      }
      else body.target = target;
      const created = await api.createCheck(body);
      setName('');
      setTarget('');
      setHost('');
      setPort('');
      setSite('');
      onCreated();
      // The ping URL only exists after creation, so go straight to where it is shown.
      if (type === 'CRON') navigate(`/checks/${created.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="panel add" onSubmit={submit}>
      <div className="panel-title">
        <h2>New check</h2>
        {onCancel && <button type="button" className="btn btn-quiet" onClick={onCancel}>Cancel</button>}
      </div>
      <div className="segmented" role="group" aria-label="Check type">
        {TYPES.map((t) => (
          <button key={t.value} type="button" className="seg" aria-pressed={type === t.value}
            onClick={() => setType(t.value)}>
            <span className="seg-icon" aria-hidden="true">{t.icon}</span>{t.label}
          </button>
        ))}
      </div>
      <div className="add-grid">
        <label className="field">
          <span>Name</span>
          <input required value={name} onChange={(e) => setName(e.target.value)}
            placeholder={type === 'CRON' ? 'Nightly backup' : 'Marketing site'} />
        </label>
        {type === 'CRON' ? (
          <>
            <label className="field">
              <span>Job runs every</span>
              <select value={periodSeconds} onChange={(e) => setPeriodSeconds(e.target.value)}>
                {PERIODS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
              </select>
            </label>
            <label className="field">
              <span>Allowed delay (minutes)</span>
              <input required type="number" min="0" max="1440" value={graceMinutes}
                onChange={(e) => setGraceMinutes(e.target.value)} />
            </label>
          </>
        ) : type === 'TLS' ? (
          <>
            <label className="field field-wide">
              <span>Host</span>
              <input required value={host} onChange={(e) => setHost(e.target.value)} placeholder="example.com" />
            </label>
            <label className="field">
              <span>Port (default 443)</span>
              <input type="number" min="1" max="65535" value={port}
                onChange={(e) => setPort(e.target.value)} placeholder="443" />
            </label>
            <label className="field">
              <span>Warn before expiry (days)</span>
              <input required type="number" min="1" max="365" value={warnDays}
                onChange={(e) => setWarnDays(e.target.value)} />
            </label>
          </>
        ) : type === 'TCP' ? (
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
          <span>Group (optional)</span>
          <input value={site} onChange={(e) => setSite(e.target.value)} placeholder="example.com" />
        </label>
        {type !== 'CRON' && (
          <label className="field">
            <span>Check every</span>
            <select value={intervalSeconds} onChange={(e) => setIntervalSeconds(e.target.value)}>
              {INTERVALS.map((i) => <option key={i.value} value={i.value}>{i.label}</option>)}
            </select>
          </label>
        )}
        <div className="add-actions">
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Adding…' : 'Add check'}</button>
        </div>
        <ErrorNote error={error} />
      </div>
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

function Summary({ items }) {
  const count = (states) => items.filter((i) => states.includes(i.check.state)).length;
  const cards = [
    { cls: 'sum-total', label: 'Checks', value: items.length },
    { cls: 'sum-up', label: 'Up', value: count(['HEALTHY']) },
    { cls: 'sum-down', label: 'Down', value: count(['DOWN', 'DEGRADED']) },
    { cls: 'sum-idle', label: 'Paused / waiting', value: count(['PAUSED', 'PENDING']) },
  ];
  return (
    <div className="summary">
      {cards.map((c) => (
        <div key={c.label} className={`sum-card ${c.cls}${c.value > 0 ? ' sum-alert' : ''}`}>
          <div className="sum-label">{c.label}</div>
          <div className="sum-value">{c.value}</div>
        </div>
      ))}
    </div>
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
        <div>
          <div className="eyebrow">Overview</div>
          <h1 className={down.length ? 'headline headline-down' : 'headline'}>
            {loading ? 'Loading checks…' : headline}
          </h1>
        </div>
        {items.length > 0 && !adding && (
          <button className="btn btn-primary" onClick={() => setAdding(true)}>+ Add check</button>
        )}
      </div>

      {error && data === null && <ErrorNote error={error} />}

      {items.length > 0 && <Summary items={items} />}

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
              <Link to={`/checks/${check.id}`}
                className={`row tone-${(STATE[check.state] || STATE.PENDING).tone}`}>
                <div className="row-main">
                  <div className="row-name">
                    {check.name} <TypeTag type={check.type} />
                  </div>
                  <div className="row-target">{check.target}</div>
                </div>
                <div className="row-tape"><Tape runs={runs} slots={30} /></div>
                <div className="row-state">
                  <StateBadge state={check.state} />
                  <div className="muted small">
                    {check.type === 'CRON'
                      ? check.lastPingAt ? `Last ping ${timeAgo(check.lastPingAt)}` : 'No ping yet'
                      : runs.length
                        ? `${runs[0].latencyMs} ms · ${timeAgo(runs[0].startedAt)}`
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
