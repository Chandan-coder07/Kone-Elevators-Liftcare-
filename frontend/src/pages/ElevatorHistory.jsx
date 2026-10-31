import { useState } from 'react';
import { api } from '../api.js';
import { Priority, Status, Err, fmt, mins, useLoad } from '../ui.jsx';

export default function ElevatorHistory() {
  const els = useLoad(() => api.get('/analytics/elevators'), []);
  const [id, setId] = useState('');
  const hist = useLoad(() => (id ? api.get(`/analytics/elevators/${id}/history`) : Promise.resolve([])), [id]);
  return (
    <div>
      <div className="row between"><h2>Service history</h2>
        <select value={id} onChange={(e) => setId(e.target.value)} aria-label="Elevator"><option value="">Choose an elevator…</option>
          {(els.data || []).map((e) => <option key={e.id} value={e.id}>{e.building_name} · {e.label}</option>)}</select></div>
      <Err e={els.error || hist.error} />
      {!id && <p className="empty">Choose an elevator to see every fault, repair and part replaced.</p>}
      {id && (hist.data || []).length === 0 && !hist.loading && <p className="empty">No incidents recorded for this elevator.</p>}
      <div className="scroll">{id && (hist.data || []).length > 0 && <table>
        <thead><tr><th>Reported</th><th>Fault</th><th>Priority</th><th>Status</th><th>Time to repair</th><th>Root cause</th><th>Parts</th><th>Technician</th></tr></thead>
        <tbody>{hist.data.map((r) => <tr key={r.incident_id} className={r.red_flag ? 'flagged' : ''}>
          <td>{fmt(r.reported_at)}<br /><span className="muted">{r.incident_id}</span></td><td>{r.category.replaceAll('_', ' ').toLowerCase()}</td><td><Priority p={r.priority} /></td>
          <td><Status s={r.status} />{r.red_flag && <span className="muted"> · missed deadline</span>}</td><td>{mins(r.resolution_min == null ? null : +r.resolution_min)}</td>
          <td>{r.root_cause || '—'}</td><td>{r.parts_replaced || '—'}</td><td>{r.technician_name || '—'}</td></tr>)}</tbody></table>}</div>
    </div>
  );
}
