import { useState } from 'react';
import { api, getSession } from '../api.js';
import { Countdown, Priority, Status, NoticeStatus, Err, fmt, useLoad } from '../ui.jsx';

export default function CompanyDashboard() {
  const [view, setView] = useState('escalations');
  const rows = useLoad(() => api.get(`/company/${view}`), [view], 15000);
  const techs = useLoad(() => api.get('/company/technicians'), []);
  const [sel, setSel] = useState(null); const [err, setErr] = useState(null);
  const canAct = ['COMPANY_SUPERVISOR', 'ADMIN'].includes(getSession()?.role);
  const canVerify = canAct || getSession()?.role === 'BUILDING_MANAGER';
  const list = rows.data || [];

  async function run(fn) { setErr(null); try { await fn(); rows.reload(); techs.reload(); } catch (e) { setErr(e); } }

  return (
    <div>
      <div className="row between"><h2>Escalations and overdue jobs</h2>
        <div className="seg" role="tablist">{['escalations', 'overdue'].map((v) => <button key={v} role="tab" aria-selected={view === v} onClick={() => setView(v)}>{v === 'escalations' ? 'Escalated' : 'Overdue'}</button>)}</div></div>
      <Err e={rows.error || err} />
      {!rows.loading && list.length === 0 && <p className="empty">Nothing {view === 'overdue' ? 'overdue' : 'escalated'} right now.</p>}
      <div className="scroll"><table>
        <thead><tr><th>Priority</th><th>Elevator</th><th>Fault</th><th>Status</th><th>Resolve clock</th><th>Why escalated</th><th>Notice</th><th>Technician</th><th></th></tr></thead>
        <tbody>{list.map((r) => (
          <tr key={r.incident_id} className={r.red_flag ? 'flagged' : ''} onClick={() => setSel(r.incident_id)}>
            <td><Priority p={r.priority} /></td>
            <td><strong>{r.elevator_label}</strong><br /><span className="muted">{r.building_name}</span></td>
            <td>{r.category.replaceAll('_', ' ').toLowerCase()}<br /><span className="muted">{r.incident_id}</span></td>
            <td><Status s={r.status} /></td>
            <td><Countdown due={r.resolution_due} stopped={r.status === 'RESOLVED_PENDING_VERIFICATION'} /></td>
            <td>{r.escalation_reason || '—'}<br /><span className="muted">{fmt(r.escalated_at)}</span></td>
            <td><NoticeStatus s={r.escalation_notice_status} /></td>
            <td onClick={(e) => e.stopPropagation()}>
              {canAct && r.status !== 'RESOLVED_PENDING_VERIFICATION' ? (
                <select value={r.technician_id || ''} aria-label={`Technician for ${r.incident_id}`}
                  onChange={(e) => e.target.value && run(() => api.post(`/company/incidents/${r.incident_id}/assign`, { technicianId: Number(e.target.value) }))}>
                  <option value="">Assign…</option>
                  {(techs.data || []).map((t) => <option key={t.id} value={t.id}>{t.name} ({t.open_jobs} open)</option>)}
                </select>) : (r.technician_name || '—')}
            </td>
            <td onClick={(e) => e.stopPropagation()}>
              {canVerify && r.status === 'RESOLVED_PENDING_VERIFICATION' && <div className="row">
                <button className="primary" onClick={() => run(() => api.post(`/company/incidents/${r.incident_id}/verify`, { approved: true }))}>Verify repair</button>
                <button onClick={() => run(() => api.post(`/company/incidents/${r.incident_id}/verify`, { approved: false, note: 'Fault still present' }))}>Reject</button></div>}
            </td>
          </tr>))}</tbody></table></div>
      {sel && <Notices incidentId={sel} canAck={canAct} onClose={() => setSel(null)} />}
    </div>
  );
}

function Notices({ incidentId, canAck, onClose }) {
  const n = useLoad(() => api.get(`/notifications?incidentId=${incidentId}`), [incidentId], 10000);
  return (
    <aside className="panel"><div className="row between"><h3>Notifications for {incidentId}</h3><button onClick={onClose}>Close</button></div>
      <Err e={n.error} />
      {(n.data || []).length === 0 && <p className="empty">No notifications were queued for this incident.</p>}
      <ul className="notices">{(n.data || []).map((m) => (
        <li key={m.id}><div className="row between"><span>{m.channel} to {m.recipient}</span><NoticeStatus s={m.status} /></div>
          <small className="muted">{m.kind.toLowerCase()} · queued {fmt(m.created_at)}{m.sent_at ? ` · sent ${fmt(m.sent_at)}` : ''}{m.delivered_at ? ` · delivered ${fmt(m.delivered_at)}` : ''}{m.attempts ? ` · ${m.attempts} attempt(s)` : ''}</small>
          {m.last_error && <small className="error">{m.last_error}</small>}
          {canAck && ['SENT', 'DELIVERED'].includes(m.status) && <button onClick={() => api.post(`/notifications/${m.id}/acknowledge`).then(n.reload)}>Mark as seen</button>}</li>))}</ul>
    </aside>
  );
}
