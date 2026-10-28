import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { ErrorNote, StateBadge, Tape, TypeTag } from '../components.jsx';
import { clock, duration, timeAgo, usePolling } from '../format.js';

function human(seconds) {
  if (seconds % 86400 === 0) return `${seconds / 86400} day${seconds === 86400 ? '' : 's'}`;
  if (seconds % 3600 === 0) return `${seconds / 3600} hour${seconds === 3600 ? '' : 's'}`;
  if (seconds % 60 === 0) return `${seconds / 60} minute${seconds === 60 ? '' : 's'}`;
  return `${seconds} seconds`;
}

/** Where a CRON check's job reports in. The token in the URL is the job's only credential. */
function PingSetup({ check }) {
  const url = `${window.location.origin}/ping/${check.pingToken}`;
  const period = check.config.periodSeconds;
  const grace = check.config.graceSeconds;
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard blocked: the URL is still selectable */
    }
  }

  return (
    <section className="panel">
      <div className="panel-title">
        <h2>Ping URL</h2>
        <span className="muted small">
          Last ping: {check.lastPingAt
            ? `${timeAgo(check.lastPingAt)}${check.lastPingStatus === 'FAIL' ? ' (reported a failure)' : ''}`
            : 'none yet'}
        </span>
      </div>
      <p className="muted measure">
        Call this URL at the end of the job. Sentra checks right after each ping and again at the deadline:
        if no ping arrives within {human(period)}{grace ? ` plus ${human(grace)} grace` : ''}, the check fails.
        Keep it secret: anyone with the URL can mark the job as healthy.
      </p>
      <div className="copy-row">
        <p className="webhook-url">{url}</p>
        <button className="btn" onClick={copy}>{copied ? 'Copied ✓' : 'Copy URL'}</button>
      </div>
      <p className="muted small measure" style={{ marginTop: '0.9rem' }}>
        Add it to the job after <code>&&</code>, so it only pings when the job succeeded:
      </p>
      <p className="webhook-url">your-job.sh &amp;&amp; curl -fsS --retry 3 {url}</p>
      <p className="muted small measure" style={{ marginTop: '0.6rem' }}>
        Optional: call <code>/start</code> when the job begins and <code>/fail</code> to report a failure straight away.
      </p>
    </section>
  );
}

function Webhook({ checkId, rules, onSaved }) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const rule = rules[0];

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.createRule(checkId, url);
      setUrl('');
      onSaved();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <div className="panel-title"><h2>Webhook alerts</h2></div>
      <p className="muted measure">
        Sentra sends a POST with the incident details when an incident opens, again when it closes, and
        a reminder at 15, 45 and 105 minutes while it stays open, then hourly.
      </p>
      {rule ? (
        <p className="webhook-url">{rule.targetUrl}</p>
      ) : (
        <form className="inline-form" onSubmit={save}>
          <label className="field field-wide">
            <span>Webhook URL</span>
            <input required type="url" value={url} onChange={(e) => setUrl(e.target.value)}
              placeholder="https://hooks.example.com/sentra" />
          </label>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save webhook'}</button>
          <ErrorNote error={error} />
        </form>
      )}
    </section>
  );
}

/** Pause/resume and delete. Delete asks for a second click, because it also removes all history. */
function DangerZone({ check, onChanged }) {
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const paused = check.state === 'PAUSED';

  async function act(fn) {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  const togglePause = () => act(async () => {
    await (paused ? api.resumeCheck(check.id) : api.pauseCheck(check.id));
    await onChanged();
  });
  const remove = () => act(async () => {
    await api.deleteCheck(check.id);
    navigate('/');
  });

  return (
    <section className="panel danger">
      <div className="panel-title"><h2>Manage check</h2></div>
      <div className="danger-row">
        <div>
          <h3>{paused ? 'Resume monitoring' : 'Pause monitoring'}</h3>
          <p className="muted small measure">
            {paused
              ? 'Start checking again. The check begins fresh, waiting for its first result.'
              : 'Stop checking for now and close any open incident quietly. History is kept.'}
          </p>
        </div>
        <button className="btn" onClick={togglePause} disabled={busy}>{paused ? '▶ Resume' : '⏸ Pause'}</button>
      </div>
      <div className="danger-row">
        <div>
          <h3>Delete this check</h3>
          <p className="muted small measure">Removes the check with all its runs, incidents and alert settings. This cannot be undone.</p>
        </div>
        {confirming ? (
          <div className="confirm">
            <span className="small fail-text">Delete permanently?</span>
            <button className="btn btn-solid-danger" onClick={remove} disabled={busy}>
              {busy ? 'Deleting…' : 'Yes, delete'}
            </button>
            <button className="btn btn-quiet" onClick={() => setConfirming(false)} disabled={busy}>Cancel</button>
          </div>
        ) : (
          <button className="btn btn-danger" onClick={() => setConfirming(true)}>Delete check</button>
        )}
      </div>
      {error && <div style={{ marginTop: '0.9rem' }}><ErrorNote error={error} /></div>}
    </section>
  );
}

export default function CheckDetail() {
  const { id } = useParams();
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState(null);

  const { data, error, refresh } = usePolling(async () => {
    const [check, runs, incidents, rules] = await Promise.all([
      api.check(id), api.runs(id), api.checkIncidents(id), api.rules(id),
    ]);
    return { check, runs, incidents, rules };
  }, 5000, [id]);

  async function runNow() {
    setRunning(true);
    setRunError(null);
    try {
      await api.runNow(id);
      await refresh();
    } catch (err) {
      setRunError(err);
    } finally {
      setRunning(false);
    }
  }

  if (error && !data) {
    return (
      <>
        <Link to="/" className="back">← All checks</Link>
        <ErrorNote error={error} />
      </>
    );
  }
  if (!data) return <p className="muted">Loading check…</p>;

  const { check, runs, incidents, rules } = data;
  const ok = runs.filter((r) => r.success);
  const successRate = runs.length ? Math.round((ok.length / runs.length) * 100) : null;
  const avgLatency = ok.length ? Math.round(ok.reduce((n, r) => n + r.latencyMs, 0) / ok.length) : null;
  const cron = check.type === 'CRON';

  return (
    <>
      <Link to="/" className="back">← All checks</Link>

      <div className="hero">
        <div>
          <div className="hero-title">
            <h1 className="headline">{check.name}</h1>
            <TypeTag type={check.type} />
          </div>
          <p className="target">{check.target}</p>
        </div>
        <div className="head-actions">
          <StateBadge state={check.state} />
          <button className="btn btn-primary" onClick={runNow} disabled={running}>
            {running ? 'Running…' : '↻ Run now'}
          </button>
        </div>
      </div>
      <ErrorNote error={runError} />

      <section className="panel">
        <div className="panel-title">
          <h2>Recent activity</h2>
          <span className="muted small">Last {Math.min(runs.length, 50)} runs</span>
        </div>
        <Tape runs={runs} slots={50} tall />
        <p className="muted small tape-key">
          Oldest on the left. Taller bars are slower responses; red bars failed.
        </p>
        <dl className="stats">
          <div><dt>Success rate</dt><dd>{successRate === null ? '—' : `${successRate}%`}</dd></div>
          <div><dt>{cron ? 'Last ping' : 'Avg response'}</dt><dd>
            {cron ? timeAgo(check.lastPingAt) : avgLatency === null ? '—' : `${avgLatency} ms`}
          </dd></div>
          <div><dt>Last checked</dt><dd>{runs.length ? timeAgo(runs[0].startedAt) : 'Not yet'}</dd></div>
          <div><dt>Schedule</dt><dd>
            {cron ? `Every ${human(check.config.periodSeconds)}` : `Every ${check.intervalSeconds}s`}
          </dd></div>
        </dl>
        <p className="muted small">
          {check.state === 'PAUSED'
            ? 'Paused. Resume it below to start checking again.'
            : check.state === 'DOWN'
              ? `Down. It recovers after ${check.recoveryThreshold} successful run${check.recoveryThreshold === 1 ? '' : 's'} in a row (${check.consecutiveSuccesses} so far).`
              : check.consecutiveFailures > 0
                ? `${check.consecutiveFailures} of ${check.failureThreshold} failures in a row before an incident opens.`
                : `An incident opens after ${check.failureThreshold} failure${check.failureThreshold === 1 ? '' : 's'} in a row.`}
        </p>
      </section>

      {cron && check.pingToken && <PingSetup check={check} />}

      <section className="panel">
        <div className="panel-title">
          <h2>Incidents</h2>
          <span className="muted small">{incidents.length} total</span>
        </div>
        {incidents.length === 0 ? (
          <p className="muted">No incidents yet. 🎉</p>
        ) : (
          <ul className="list">
            {incidents.map((i) => (
              <li key={i.id} className="list-row">
                <span className={i.closedAt ? 'badge badge-up' : 'badge badge-down'}>
                  <span className="dot" aria-hidden="true" />
                  {i.closedAt ? 'Resolved' : 'Open'}
                </span>
                <span>
                  Opened {clock(i.openedAt)} · {i.closedAt ? `lasted ${duration(i.openedAt, i.closedAt)}` : `open for ${duration(i.openedAt)}`}
                </span>
                <span className="muted grow small">{i.lastError}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Webhook checkId={id} rules={rules} onSaved={refresh} />

      <section className="panel">
        <div className="panel-title"><h2>Recent runs</h2></div>
        {runs.length === 0 ? (
          <p className="muted">No runs yet. The first one starts within a few seconds, or use Run now.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr><th>Time</th><th>Result</th><th className="num">Response</th></tr>
              </thead>
              <tbody>
                {runs.slice(0, 20).map((r) => (
                  <tr key={r.id}>
                    <td>{clock(r.startedAt)}</td>
                    <td className={r.success ? 'ok-text' : 'fail-text'}>
                      {r.success
                        ? r.detail || `OK, HTTP ${r.statusCode}`
                        : r.error || `HTTP ${r.statusCode}`}
                    </td>
                    <td className="num">{r.latencyMs} ms</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <DangerZone check={check} onChanged={refresh} />
    </>
  );
}
