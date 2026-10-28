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

const UNITS = [
  { seconds: 1, label: 'seconds' },
  { seconds: 60, label: 'minutes' },
  { seconds: 3600, label: 'hours' },
  { seconds: 86400, label: 'days' },
];

const PERIOD_PRESETS = [
  { seconds: 30, label: '30 s' },
  { seconds: 60, label: '1 min' },
  { seconds: 300, label: '5 min' },
  { seconds: 900, label: '15 min' },
  { seconds: 3600, label: '1 hour' },
  { seconds: 86400, label: '1 day' },
  { seconds: 604800, label: '1 week' },
];

/** Same rule as CronProbe.defaultGrace on the backend: 20% of the period, between 10 s and 1 hour. */
const defaultGrace = (period) => Math.max(10, Math.min(3600, Math.round(period * 0.2)));

/** Largest unit that divides the value exactly, e.g. 900 -> 15 minutes. */
function split(seconds) {
  const unit = [...UNITS].reverse().find((u) => seconds % u.seconds === 0) || UNITS[0];
  return { value: String(seconds / unit.seconds), unit: unit.seconds };
}

function human(seconds) {
  const { value, unit } = split(seconds);
  const label = UNITS.find((u) => u.seconds === unit).label;
  return `${value} ${value === '1' ? label.slice(0, -1) : label}`;
}

/** "Job runs every" with any value and unit, plus the allowed delay derived from it. */
function CronSchedule({ period, setPeriod, grace, setGrace, graceTouched, setGraceTouched }) {
  // Units are remembered separately so clearing a number box does not flip its unit.
  const [periodUnit, setPeriodUnit] = useState(split(period).unit);
  const [graceUnit, setGraceUnit] = useState(split(grace).unit);
  const p = { value: period ? String(period / periodUnit) : '', unit: periodUnit };
  const g = { value: String(grace / graceUnit), unit: graceUnit };
  const onPeriod = (seconds, unit = periodUnit) => {
    setPeriodUnit(unit);
    setPeriod(seconds);
    if (!graceTouched) {
      const auto = defaultGrace(seconds);
      setGrace(auto);
      setGraceUnit(split(auto).unit);
    }
  };
  return (
    <>
      <div className="field field-wide">
        <span>Job runs every</span>
        <div className="unit-input">
          <input required type="number" min="1" value={p.value} aria-label="Job period"
            onChange={(e) => onPeriod(Math.max(0, Number(e.target.value)) * p.unit)} />
          <select value={p.unit} aria-label="Period unit"
            onChange={(e) => onPeriod(Number(p.value) * Number(e.target.value), Number(e.target.value))}>
            {UNITS.map((u) => <option key={u.seconds} value={u.seconds}>{u.label}</option>)}
          </select>
        </div>
        <div className="chips">
          {PERIOD_PRESETS.map((x) => (
            <button key={x.seconds} type="button" className="chip" aria-pressed={period === x.seconds}
              onClick={() => onPeriod(x.seconds, split(x.seconds).unit)}>{x.label}</button>
          ))}
        </div>
      </div>
      <div className="field">
        <span>Allowed delay {graceTouched ? '' : '(auto: 20%)'}</span>
        <div className="unit-input">
          <input required type="number" min="0" value={g.value} aria-label="Allowed delay"
            onChange={(e) => { setGraceTouched(true); setGrace(Math.max(0, Number(e.target.value)) * g.unit); }} />
          <select value={g.unit} aria-label="Delay unit"
            onChange={(e) => {
              setGraceTouched(true);
              setGraceUnit(Number(e.target.value));
              setGrace(Number(g.value) * Number(e.target.value));
            }}>
            {UNITS.map((u) => <option key={u.seconds} value={u.seconds}>{u.label}</option>)}
          </select>
        </div>
        {graceTouched && (
          <button type="button" className="link small" style={{ alignSelf: 'flex-start' }}
            onClick={() => {
              const auto = defaultGrace(period);
              setGraceTouched(false);
              setGrace(auto);
              setGraceUnit(split(auto).unit);
            }}>
            Reset to auto
          </button>
        )}
      </div>
      {period >= 10 && (
        <p className="schedule-note">
          <b>How Sentra watches this job:</b> it expects a ping every <b>{human(period)}</b>. It checks
          right after each ping, and once more at the deadline. If no ping arrives within{' '}
          <b>{human(period)} + {human(grace)} = {human(period + grace)}</b> of the last one, the job is
          marked <b>DOWN</b>. That is about {Math.round(86400 / period).toLocaleString()} check
          {Math.round(86400 / period) === 1 ? '' : 's'} a day, one per run of the job, never a fixed timer.
        </p>
      )}
      {period > 0 && period < 10 && (
        <p className="schedule-note schedule-warn">The shortest supported schedule is 10 seconds.</p>
      )}
    </>
  );
}

const TYPES = [
  { value: 'HTTP', label: 'Website', icon: '🌐' },
  { value: 'TCP', label: 'Port', icon: '🔌' },
  { value: 'TLS', label: 'Certificate', icon: '🔒' },
  { value: 'DOMAIN', label: 'Domain', icon: '📅' },
  { value: 'CRON', label: 'Cron job', icon: '⏱' },
];

// Registries rate-limit and expiry dates move over days, so a domain check runs hourly at most.
const DOMAIN_INTERVALS = INTERVALS.filter((i) => i.value >= 3600);

function AddCheck({ onCreated, onCancel }) {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [type, setType] = useState('HTTP');
  const [target, setTarget] = useState('');
  const [host, setHost] = useState('');
  const [port, setPort] = useState('');
  const [warnDays, setWarnDays] = useState('14');
  const [domainWarnDays, setDomainWarnDays] = useState('30');
  const [site, setSite] = useState('');
  const [intervalSeconds, setIntervalSeconds] = useState(60);
  const [periodSeconds, setPeriodSeconds] = useState(3600);
  const [graceSeconds, setGraceSeconds] = useState(defaultGrace(3600));
  const [graceTouched, setGraceTouched] = useState(false);
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
      else if (type === 'DOMAIN') {
        Object.assign(body, { host, warnDays: Number(domainWarnDays) });
        if (body.intervalSeconds < 3600) body.intervalSeconds = 86400;
      }
      else if (type === 'CRON') {
        Object.assign(body, { periodSeconds, graceSeconds });
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
            onClick={() => {
              if (t.value !== type) setIntervalSeconds(t.value === 'DOMAIN' ? 86400 : 60);
              setType(t.value);
            }}>
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
          <CronSchedule period={periodSeconds} setPeriod={setPeriodSeconds}
            grace={graceSeconds} setGrace={setGraceSeconds}
            graceTouched={graceTouched} setGraceTouched={setGraceTouched} />
        ) : type === 'DOMAIN' ? (
          <>
            <label className="field field-wide">
              <span>Domain</span>
              <input required value={host} onChange={(e) => setHost(e.target.value)} placeholder="example.com" />
            </label>
            <label className="field">
              <span>Warn before expiry (days)</span>
              <input required type="number" min="1" max="365" value={domainWarnDays}
                onChange={(e) => setDomainWarnDays(e.target.value)} />
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
              {(type === 'DOMAIN' ? DOMAIN_INTERVALS : INTERVALS)
                .map((i) => <option key={i.value} value={i.value}>{i.label}</option>)}
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
                      : check.type === 'DOMAIN'
                        ? runs[0]?.detail || runs[0]?.error || 'Checked daily'
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
