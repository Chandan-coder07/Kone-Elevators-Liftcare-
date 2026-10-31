import { useEffect, useState } from 'react';

export const fmt = (iso) => iso ? new Date(iso).toLocaleString([], { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';
export const mins = (v) => v == null ? '—' : v >= 120 ? `${(v / 60).toFixed(1)} h` : `${Math.round(v)} min`;

/** Elevator-style countdown to a deadline. Turns red and counts up once overdue. */
export function Countdown({ due, stopped }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, []);
  if (!due) return <span className="display idle">--:--:--</span>;
  const diff = new Date(due).getTime() - now, over = diff < 0, a = Math.abs(diff);
  const p = (n) => String(Math.floor(n)).padStart(2, '0');
  const txt = `${p(a / 3600000)}:${p((a / 60000) % 60)}:${p((a / 1000) % 60)}`;
  return <span className={'display ' + (stopped ? 'idle' : over ? 'over' : '')} title={over ? 'Past the resolution deadline' : 'Time left to resolve'}>{over && !stopped ? '+' : ''}{txt}</span>;
}

export const Priority = ({ p }) => <span className={'prio ' + p}>{p}</span>;

const LABEL = { ASSIGNED: 'Assigned', ACKNOWLEDGED: 'Acknowledged', TECHNICIAN_ARRIVED: 'On site', IN_PROGRESS: 'Repairing', PARTS_DELAYED: 'Waiting for parts',
  RESOLVED_PENDING_VERIFICATION: 'Repaired, needs verification', VERIFIED_RESOLVED: 'Closed', REPORTED: 'Reported' };
export const Status = ({ s }) => <span className={'chip st-' + s}>{LABEL[s] || s}</span>;

const NLABEL = { PENDING: 'Queued', RETRY: 'Retrying', SENT: 'Sent, not confirmed', DELIVERED: 'Delivered', FAILED: 'Failed', ACKNOWLEDGED: 'Acknowledged' };
export const NoticeStatus = ({ s }) => s ? <span className={'chip nt-' + s}>{NLABEL[s] || s}</span> : <span className="muted">No notice</span>;

export function useLoad(fn, deps = [], every = 0) {
  const [state, set] = useState({ data: null, error: null, loading: true });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    fn().then((data) => live && set({ data, error: null, loading: false })).catch((error) => live && set((s) => ({ ...s, error, loading: false })));
    return () => { live = false; };
  }, [...deps, tick]);
  useEffect(() => { if (!every) return; const t = setInterval(() => setTick((x) => x + 1), every); return () => clearInterval(t); }, [every]);
  return { ...state, reload: () => setTick((x) => x + 1) };
}

export const Err = ({ e }) => e ? <p className="error" role="alert">{e.message}</p> : null;
