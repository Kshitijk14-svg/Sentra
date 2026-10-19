const TOKEN_KEY = 'sentra.token';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (t) => localStorage.setItem(TOKEN_KEY, t);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

// The backend's default error bodies carry no useful message, so map status codes to plain language.
function friendly(status, data, path) {
  if (status === 400) {
    // SsrfExceptionHandler returns {"error": "..."}; Spring's own 400 body uses the generic reason phrase.
    if (data && data.error && data.error !== 'Bad Request') return data.error;
    return 'Check the fields and try again.';
  }
  if (status === 401) return path.startsWith('/auth') ? 'Wrong email or password.' : 'Your session expired.';
  if (status === 409) {
    return path.includes('/auth/register')
      ? 'That email already has an account.'
      : 'This check already has a webhook.';
  }
  if (status === 404) return 'Not found.';
  return 'Something went wrong. Try again.';
}

async function request(method, path, body) {
  const token = getToken();
  let res;
  try {
    res = await fetch('/api/v1' + path, {
      method,
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running?');
  }
  if (res.status === 401 && !path.startsWith('/auth')) {
    clearToken();
    window.dispatchEvent(new Event('sentra:logout'));
  }
  if (!res.ok) {
    let data = null;
    try {
      data = await res.json();
    } catch {
      /* body is optional */
    }
    throw new ApiError(res.status, friendly(res.status, data, path));
  }
  return res.status === 204 ? null : res.json();
}

export const api = {
  register: (email, password) => request('POST', '/auth/register', { email, password }),
  login: (email, password) => request('POST', '/auth/login', { email, password }),
  checks: () => request('GET', '/checks'),
  check: (id) => request('GET', `/checks/${id}`),
  createCheck: (body) => request('POST', '/checks', body),
  runNow: (id) => request('POST', `/checks/${id}/run`),
  runs: (id) => request('GET', `/checks/${id}/runs`),
  checkIncidents: (id) => request('GET', `/checks/${id}/incidents`),
  incidents: () => request('GET', '/incidents'),
  rules: (id) => request('GET', `/checks/${id}/alert-rules`),
  createRule: (id, url) => request('POST', `/checks/${id}/alert-rules`, { url }),
};

/** Reads the email claim from the JWT payload (display only; the server is what verifies the token). */
export function emailFromToken(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)).email || '';
  } catch {
    return '';
  }
}
