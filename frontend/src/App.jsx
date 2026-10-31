import { useEffect, useState } from 'react';
import { api, getSession, setSession } from './api.js';
import { flush, queued, rejected, clearRejected } from './offlineQueue.js';
import TechnicianPortal from './pages/TechnicianPortal.jsx';
import CompanyDashboard from './pages/CompanyDashboard.jsx';
import Analytics from './pages/Analytics.jsx';
import ElevatorHistory from './pages/ElevatorHistory.jsx';

// Dev sign-in matching database/seed.sql. Replace with Member 1's login page.
const USERS = [{ id: 3, name: 'Ravi (Technician)', role: 'TECHNICIAN' }, { id: 4, name: 'Mani (Technician)', role: 'TECHNICIAN' },
  { id: 5, name: 'Priya (Service Supervisor)', role: 'COMPANY_SUPERVISOR' }, { id: 6, name: 'Kumar (Building Manager)', role: 'BUILDING_MANAGER' }, { id: 1, name: 'Admin', role: 'ADMIN' }];
const TABS = { TECHNICIAN: [['jobs', 'My jobs']], default: [['escalations', 'Escalations'], ['analytics', 'Analytics'], ['history', 'Service history']] };

export default function App() {
  const [session, setS] = useState(getSession());
  const [online, setOnline] = useState(navigator.onLine);
  const [q, setQ] = useState(queued().length); const [rej, setRej] = useState(rejected());
  const [sync, setSync] = useState(null);
  const tabs = session ? (TABS[session.role] || TABS.default) : [];
  const [tab, setTab] = useState(null);
  const current = tab && tabs.find((t) => t[0] === tab) ? tab : tabs[0]?.[0];

  useEffect(() => {
    const on = () => { setOnline(true); flush(); }, off = () => setOnline(false), refresh = () => { setQ(queued().length); setRej(rejected()); };
    window.addEventListener('online', on); window.addEventListener('offline', off); window.addEventListener('liftcare-queue', refresh);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); window.removeEventListener('liftcare-queue', refresh); };
  }, []);
  useEffect(() => { if (!session) return; const f = () => api.get('/sync/status').then(setSync).catch(() => setSync(null)); f(); const t = setInterval(f, 20000); return () => clearInterval(t); }, [session]);

  if (!session) return (
    <main className="signin"><h1>LiftCare Service</h1><p>Choose who you are signing in as. (Development sign-in; the team login replaces this.)</p>
      {USERS.map((u) => <button key={u.id} onClick={() => { setSession(u); setS(u); }}>{u.name}</button>)}</main>);

  return (
    <>
      <header>
        <div className="brand"><strong>LiftCare</strong><span>Service desk</span></div>
        <nav>{tabs.map(([k, l]) => <button key={k} aria-current={current === k} onClick={() => setTab(k)}>{l}</button>)}</nav>
        <div className="status">
          <span className={'dot ' + (online ? 'on' : 'off')} />{online ? 'Connected' : 'Offline'}
          {q > 0 && <span className="chip">{q} action{q > 1 ? 's' : ''} waiting to send</span>}
          {sync?.enabled && <span className="chip">{sync.pendingEvents} update{sync.pendingEvents === 1 ? '' : 's'} not yet at building{sync.lastError ? ' (building server unreachable)' : ''}</span>}
          <button onClick={() => { localStorage.removeItem('liftcare.session'); setS(null); }}>{session.name} · Sign out</button>
        </div>
      </header>
      {rej.length > 0 && <div className="banner" role="alert">{rej.length} offline action(s) were rejected by the server: {rej.map((r) => `${r.incidentId} ${r.action} (${r.reason})`).join('; ')}. <button onClick={clearRejected}>Dismiss</button></div>}
      <main>
        {current === 'jobs' && <TechnicianPortal />}
        {current === 'escalations' && <CompanyDashboard />}
        {current === 'analytics' && <Analytics />}
        {current === 'history' && <ElevatorHistory />}
      </main>
    </>
  );
}
