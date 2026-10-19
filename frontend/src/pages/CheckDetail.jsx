import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { ErrorNote, StateBadge, Tape } from '../components.jsx';
import { clock, duration, timeAgo, usePolling } from '../format.js';

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
    <section className="block">
      <h2>Webhook alerts</h2>
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
        <Link to="/" className="back">All checks</Link>
        <ErrorNote error={error} />
      </>
    );
  }
  if (!data) return <p className="muted">Loading check…</p>;

  const { check, runs, incidents, rules } = data;
  const ok = runs.filter((r) => r.success);
  const successRate = runs.length ? Math.round((ok.length / runs.length) * 100) : null;
  const avgLatency = ok.length ? Math.round(ok.reduce((n, r) => n + r.latencyMs, 0) / ok.length) : null;

  return (
    <>
      <Link to="/" className="back">All checks</Link>

      <div className="page-head">
        <div>
          <h1 className="headline">{check.name}</h1>
          <p className="target">{check.target}</p>
        </div>
        <div className="head-actions">
          <StateBadge state={check.state} />
          <button className="btn btn-primary" onClick={runNow} disabled={running}>
            {running ? 'Running…' : 'Run now'}
          </button>
        </div>
      </div>
      <ErrorNote error={runError} />

      <section className="block">
        <Tape runs={runs} slots={50} tall />
        <p className="muted small tape-key">
          Last {Math.min(runs.length, 50)} runs, oldest on the left. Taller bars are slower responses; red bars failed.
        </p>
        <dl className="stats">
          <div><dt>Success rate</dt><dd>{successRate === null ? 'No runs yet' : `${successRate}%`}</dd></div>
          <div><dt>Average response</dt><dd>{avgLatency === null ? 'No data' : `${avgLatency} ms`}</dd></div>
          <div><dt>Checked</dt><dd>{runs.length ? timeAgo(runs[0].startedAt) : 'Not yet'}</dd></div>
          <div><dt>Schedule</dt><dd>Every {check.intervalSeconds}s</dd></div>
        </dl>
        <p className="muted measure">
          {check.state === 'DOWN'
            ? `Down. It will recover after ${check.recoveryThreshold} successful runs in a row (${check.consecutiveSuccesses} so far).`
            : check.consecutiveFailures > 0
              ? `${check.consecutiveFailures} of ${check.failureThreshold} failures in a row before an incident opens.`
              : `An incident opens after ${check.failureThreshold} failures in a row.`}
        </p>
      </section>

      <section className="block">
        <h2>Incidents</h2>
        {incidents.length === 0 ? (
          <p className="muted">No incidents. One opens after {check.failureThreshold} failures in a row.</p>
        ) : (
          <ul className="list">
            {incidents.map((i) => (
              <li key={i.id} className="list-row">
                <span className={i.closedAt ? 'badge badge-up' : 'badge badge-down'}>
                  <span className="dot" aria-hidden="true" />
                  {i.closedAt ? 'Resolved' : 'Open'}
                </span>
                <span>
                  Opened {clock(i.openedAt)}, {i.closedAt ? `lasted ${duration(i.openedAt, i.closedAt)}` : `open for ${duration(i.openedAt)}`}
                </span>
                <span className="muted grow">{i.lastError}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Webhook checkId={id} rules={rules} onSaved={refresh} />

      <section className="block">
        <h2>Recent runs</h2>
        {runs.length === 0 ? (
          <p className="muted">No runs yet. The first one starts within a few seconds, or use Run now.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Time</th><th>Result</th><th className="num">Response</th></tr>
            </thead>
            <tbody>
              {runs.slice(0, 20).map((r) => (
                <tr key={r.id}>
                  <td>{clock(r.startedAt)}</td>
                  <td className={r.success ? 'ok-text' : 'fail-text'}>
                    {r.success ? `OK, HTTP ${r.statusCode}` : r.error || `HTTP ${r.statusCode}`}
                  </td>
                  <td className="num">{r.latencyMs} ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
