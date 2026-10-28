import { STATE } from './format.js';

/** One bar per run, oldest on the left: height is latency, a failed run is a full-height red bar. */
export function Tape({ runs, slots = 30, tall = false }) {
  const recent = runs.slice(0, slots).reverse();
  const slowest = Math.max(1, ...recent.filter((r) => r.success).map((r) => r.latencyMs));
  const failed = recent.filter((r) => !r.success).length;
  const label = recent.length
    ? `Last ${recent.length} runs, ${failed} failed`
    : 'No runs yet';

  return (
    <div className={tall ? 'tape tape-tall' : 'tape'} role="img" aria-label={label}>
      {Array.from({ length: slots - recent.length }, (_, i) => (
        <i key={`blank-${i}`} className="bar bar-blank" />
      ))}
      {recent.map((r) => (
        <i
          key={r.id}
          className={r.success ? 'bar bar-ok' : 'bar bar-fail'}
          style={{ height: r.success ? `${Math.max(24, (r.latencyMs / slowest) * 100)}%` : '100%' }}
          title={`${new Date(r.startedAt).toLocaleTimeString()}: ${
            r.success ? `${r.latencyMs} ms` : r.error || `HTTP ${r.statusCode}`
          }`}
        />
      ))}
    </div>
  );
}

export function StateBadge({ state }) {
  const s = STATE[state] || STATE.PENDING;
  return (
    <span className={`badge badge-${s.tone}`}>
      <span className="dot" aria-hidden="true" />
      {s.label}
    </span>
  );
}

export function TypeTag({ type }) {
  return <span className={`type-tag type-${type}`}>{type}</span>;
}

/** The pulse-line logo on a gradient tile, shared by the top bar and the sign-in page. */
export function BrandMark({ size = 30 }) {
  return (
    <span className="brand-mark" style={{ width: size, height: size }} aria-hidden="true">
      <svg width={size * 0.7} height={size * 0.7} viewBox="0 0 32 32">
        <path d="M3 18h6l3-9 5 15 3-9h9" fill="none" stroke="#071021" strokeWidth="3"
          strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  );
}

export function ErrorNote({ error }) {
  if (!error) return null;
  return (
    <p className="note note-error" role="alert">
      {error.message}
    </p>
  );
}
