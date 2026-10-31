// Technician actions made without a connection are stored on the device and replayed in order.
// Each carries clientActionId (server ignores repeats) and occurredAt (real time of the action).
import { api, ApiError } from './api.js';
const QKEY = 'liftcare.queue', FKEY = 'liftcare.rejected';
const read = (k) => JSON.parse(localStorage.getItem(k) || '[]');
const write = (k, v) => { localStorage.setItem(k, JSON.stringify(v)); window.dispatchEvent(new Event('liftcare-queue')); };

export const queued = () => read(QKEY);
export const rejected = () => read(FKEY);
export const clearRejected = () => write(FKEY, []);

export async function sendAction(incidentId, action, payload = {}) {
  const body = { ...payload, clientActionId: crypto.randomUUID(), occurredAt: new Date().toISOString() };
  try { return { queued: false, result: await api.post(`/technician/jobs/${incidentId}/${action}`, body) }; }
  catch (e) {
    if (e instanceof ApiError) throw e;                       // server said no: show it
    write(QKEY, [...read(QKEY), { incidentId, action, body }]); // no connection: keep it
    return { queued: true };
  }
}

let flushing = false;
export async function flush() {
  if (flushing) return; flushing = true;
  try {
    for (const item of read(QKEY)) {
      try { await api.post(`/technician/jobs/${item.incidentId}/${item.action}`, item.body); }
      catch (e) {
        if (!(e instanceof ApiError)) break;                    // still offline: stop, keep order
        write(FKEY, [...read(FKEY), { ...item, reason: e.message }]);
      }
      write(QKEY, read(QKEY).filter((q) => q.body.clientActionId !== item.body.clientActionId));
    }
  } finally { flushing = false; }
}
window.addEventListener('online', flush);
setInterval(flush, 15000);
