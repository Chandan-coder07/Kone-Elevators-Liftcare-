import { useState } from 'react';
import { api } from '../api.js';
import { sendAction } from '../offlineQueue.js';
import { Countdown, Priority, Status, Err, fmt, useLoad } from '../ui.jsx';

export default function TechnicianPortal() {
  const jobs = useLoad(() => api.get('/technician/jobs'), [], 20000);
  const [openId, setOpenId] = useState(null);
  const list = jobs.data || [];
  const open = list.find((j) => j.incident_id === openId);

  return (
    <div className="split">
      <section>
        <h2>My jobs</h2>
        <Err e={jobs.error} />
        {!jobs.loading && list.length === 0 && <p className="empty">No open jobs. New assignments appear here as soon as the supervisor sends them.</p>}
        <ul className="jobs">
          {list.map((j) => (
            <li key={j.incident_id}>
              <button className={'job' + (j.red_flag ? ' flagged' : '') + (j.incident_id === openId ? ' sel' : '')} onClick={() => setOpenId(j.incident_id)}>
                <div className="row"><Priority p={j.priority} /><strong>{j.elevator_label}</strong><span className="muted">{j.building_name}</span></div>
                <div className="row between"><span>{j.category.replaceAll('_', ' ').toLowerCase()}</span><Status s={j.status} /></div>
                <div className="row between"><span className="muted">{j.incident_id}</span><Countdown due={j.resolution_due} /></div>
              </button>
            </li>
          ))}
        </ul>
      </section>
      <section>{open ? <JobDetail key={open.incident_id} job={open} onChanged={jobs.reload} /> : <p className="empty">Select a job to see what to do next.</p>}</section>
    </div>
  );
}

function JobDetail({ job, onChanged }) {
  const tl = useLoad(() => api.get(`/technician/jobs/${job.incident_id}/timeline`), [job.status]);
  const [form, setForm] = useState({}); const [err, setErr] = useState(null); const [msg, setMsg] = useState(null); const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function go(action, payload) {
    setBusy(true); setErr(null); setMsg(null);
    try {
      const r = await sendAction(job.incident_id, action, payload);
      setMsg(r.queued ? 'No connection. Saved on this phone with the time you did it; it will send automatically.' : 'Saved.');
      setForm({}); onChanged();
    } catch (e) { setErr(e); } finally { setBusy(false); }
  }
  const s = job.status, working = ['TECHNICIAN_ARRIVED', 'IN_PROGRESS', 'PARTS_DELAYED'].includes(s);

  return (
    <div className="detail">
      <div className="row between"><h2>{job.elevator_label} · {job.incident_id}</h2><Status s={s} /></div>
      {job.is_emergency && <p className="banner">Emergency report. Follow the emergency procedure first; do not wait to acknowledge in the app.</p>}
      <p>{job.description || 'No description given.'}</p>
      <dl className="facts">
        <dt>Acknowledge by</dt><dd>{fmt(job.acknowledgement_due)}</dd>
        <dt>Arrive by</dt><dd>{fmt(job.arrival_due)}</dd>
        <dt>Resolve by</dt><dd>{fmt(job.resolution_due)}</dd>
        {job.parts_delay_reason && (<><dt>Parts delay</dt><dd>{job.parts_delay_reason}{job.parts_eta ? `, expected ${fmt(job.parts_eta)}` : ''}</dd></>)}
      </dl>

      {s === 'ASSIGNED' && <div className="row"><button className="primary" disabled={busy} onClick={() => go('acknowledge')}>Acknowledge job</button>
        <button disabled={busy} onClick={() => go('arrive')}>I’m on site</button></div>}
      {s === 'ACKNOWLEDGED' && <button className="primary" disabled={busy} onClick={() => go('arrive')}>I’m on site</button>}

      {working && (
        <div className="forms">
          <fieldset><legend>Progress note</legend>
            <textarea value={form.note || ''} onChange={set('note')} rows={2} placeholder="What you found or did" />
            <button disabled={busy || !form.note} onClick={() => go('progress', { note: form.note })}>Save note</button></fieldset>
          {s !== 'PARTS_DELAYED' && <fieldset><legend>Waiting for parts</legend>
            <input value={form.reason || ''} onChange={set('reason')} placeholder="Which part, and why the delay" />
            <label>Expected arrival <input type="datetime-local" value={form.eta || ''} onChange={set('eta')} /></label>
            <button disabled={busy || !form.reason} onClick={() => go('parts-delay', { partsDelayReason: form.reason, partsEta: form.eta ? new Date(form.eta).toISOString() : null })}>Report parts delay</button></fieldset>}
          <fieldset><legend>Mark as repaired</legend>
            <input value={form.rc || ''} onChange={set('rc')} placeholder="Root cause" />
            <textarea value={form.act || ''} onChange={set('act')} rows={2} placeholder="Work done" />
            <input value={form.pr || ''} onChange={set('pr')} placeholder="Parts replaced (optional)" />
            <button className="primary" disabled={busy || !form.rc || !form.act} onClick={() => go('resolve', { rootCause: form.rc, actionTaken: form.act, partsReplaced: form.pr })}>Mark repaired</button>
            <small className="muted">The job stays on the clock until the supervisor or building manager verifies the repair.</small></fieldset>
        </div>
      )}
      {s === 'RESOLVED_PENDING_VERIFICATION' && <p className="banner ok">Repair recorded. Waiting for verification.</p>}
      <Err e={err} />{msg && <p className="notice">{msg}</p>}

      <h3>History</h3>
      <ol className="timeline">{(tl.data || []).map((e, i) => (
        <li key={i}><time>{fmt(e.occurred_at)}</time> <strong>{e.event_type.replaceAll('_', ' ').toLowerCase()}</strong>{e.actor ? ` by ${e.actor}` : ''}{e.note ? ` — ${e.note}` : ''}</li>))}</ol>
    </div>
  );
}
