// Identity: dev stub (headers). Replace getSession() with Member 1's login token when merged.
const BASE = import.meta.env.VITE_API_URL || '/api';
const KEY = 'liftcare.session';
export const getSession = () => JSON.parse(localStorage.getItem(KEY) || 'null');
export const setSession = (s) => localStorage.setItem(KEY, JSON.stringify(s));

export class ApiError extends Error { constructor(msg, status, code) { super(msg); this.status = status; this.code = code; } }

async function req(path, opts = {}) {
  const s = getSession() || {};
  const res = await fetch(BASE + path, { ...opts, headers: { 'Content-Type': 'application/json', 'X-User-Id': s.id ?? '', 'X-User-Role': s.role ?? '', ...(opts.headers || {}) } });
  if (res.headers.get('content-type')?.includes('text/csv')) return res.text();
  const body = await res.json().catch(() => null);
  if (!res.ok || !body?.success) throw new ApiError(body?.error?.message || `Request failed (${res.status})`, res.status, body?.error?.code);
  return body.data;
}
export const api = {
  get: (p) => req(p),
  post: (p, b) => req(p, { method: 'POST', body: JSON.stringify(b ?? {}) }),
};
