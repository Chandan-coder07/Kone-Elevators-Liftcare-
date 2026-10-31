import { useState } from 'react';
import { api } from '../api.js';
import { Priority, Err, fmt, mins, useLoad } from '../ui.jsx';

export default function Analytics() {
  const [days, setDays] = useState(30);
  const sum = useLoad(() => api.get(`/analytics/summary?days=${days}`), [days]);
  const vol = useLoad(() => api.get(`/analytics/volume?days=${days}`), [days]);
  const pri = useLoad(() => api.get(`/analytics/by-priority?days=${days}`), [days]);
  const rep = useLoad(() => api.get(`/analytics/repeat-faults?days=${days}&threshold=3`), [days]);
  const s = sum.data || {};

  async function download() {
    const csv = await api.get(`/analytics/report?days=${days}`);
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); a.download = `liftcare-service-report-${days}d.csv`; a.click();
  }
  const kpis = [['Incidents', s.incidents], ['Still open', s.open_incidents], ['SLA breaches', s.sla_breaches], ['Met SLA', s.sla_met_pct != null ? s.sla_met_pct + '%' : '—'],
    ['Avg response', mins(s.avg_response_min)], ['Avg arrival', mins(s.avg_arrival_min)], ['Avg resolution', mins(s.avg_resolution_min)], ['Elevator downtime', mins(s.downtime_min)]];

  return (
    <div>
      <div className="row between"><h2>Service performance</h2>
        <div className="row"><select value={days} onChange={(e) => setDays(+e.target.value)} aria-label="Period">{[7, 30, 90].map((d) => <option key={d} value={d}>Last {d} days</option>)}</select>
          <button onClick={download}>Download report (CSV)</button></div></div>
      <Err e={sum.error || vol.error} />
      <dl className="kpis">{kpis.map(([k, v]) => <div key={k}><dd>{v ?? '—'}</dd><dt>{k}</dt></div>)}</dl>

      <h3>Incidents per day</h3>
      <Bars data={vol.data || []} />

      <h3>By priority</h3>
      <div className="scroll"><table><thead><tr><th>Priority</th><th>Incidents</th><th>SLA breaches</th><th>Avg response</th><th>Avg resolution</th></tr></thead>
        <tbody>{(pri.data || []).map((r) => <tr key={r.priority}><td><Priority p={r.priority} /></td><td>{r.incidents}</td><td>{r.breaches}</td><td>{mins(+r.avg_response_min)}</td><td>{mins(+r.avg_resolution_min)}</td></tr>)}</tbody></table></div>

      <h3>Repeat faults</h3>
      <p className="muted">Same elevator, same fault type, 3 or more times in the period. These need a root-cause review, not another quick fix.</p>
      {(rep.data || []).length === 0 ? <p className="empty">No repeat faults in this period.</p> :
        <div className="scroll"><table><thead><tr><th>Elevator</th><th>Fault</th><th>Times</th><th>First</th><th>Latest</th></tr></thead>
          <tbody>{rep.data.map((r, i) => <tr key={i}><td><strong>{r.elevator_label}</strong> <span className="muted">{r.building_name}</span></td><td>{r.category.replaceAll('_', ' ').toLowerCase()}</td><td><strong>{r.occurrences}</strong></td><td>{fmt(r.first_seen)}</td><td>{fmt(r.last_seen)}</td></tr>)}</tbody></table></div>}
    </div>
  );
}

function Bars({ data }) {
  if (!data.length) return <p className="empty">No incidents in this period.</p>;
  const max = Math.max(...data.map((d) => +d.incidents), 1), w = 18, g = 6, h = 120;
  return (
    <div className="scroll"><svg role="img" aria-label="Incidents per day" width={data.length * (w + g)} height={h + 24}>
      {data.map((d, i) => { const bh = (+d.incidents / max) * h, bb = (+d.breaches / max) * h, x = i * (w + g);
        return <g key={d.day}><title>{`${d.day}: ${d.incidents} incidents, ${d.breaches} breaches`}</title>
          <rect x={x} y={h - bh} width={w} height={bh} className="bar" /><rect x={x} y={h - bb} width={w} height={bb} className="bar breach" />
          {i % 5 === 0 && <text x={x} y={h + 16} className="tick">{d.day.slice(5)}</text>}</g>; })}</svg></div>
  );
}
