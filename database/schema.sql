-- LiftCare shared schema (tables Member 4 reads/writes).
-- Tables marked [shared] are owned by other members; keep column names identical to the team-agreed schema.
-- Columns added by Member 4 are marked [M4].

CREATE TABLE IF NOT EXISTS buildings (            -- [shared: Member 1]
  id BIGSERIAL PRIMARY KEY, name VARCHAR(120) NOT NULL, address TEXT);

CREATE TABLE IF NOT EXISTS elevators (            -- [shared: Member 1]
  id BIGSERIAL PRIMARY KEY, building_id BIGINT NOT NULL REFERENCES buildings(id),
  label VARCHAR(20) NOT NULL, model VARCHAR(80), UNIQUE (building_id, label));

CREATE TABLE IF NOT EXISTS users (                -- [shared: Member 1]
  id BIGSERIAL PRIMARY KEY, name VARCHAR(120) NOT NULL, email VARCHAR(160) UNIQUE, phone VARCHAR(30),
  role VARCHAR(30) NOT NULL CHECK (role IN ('RESIDENT','SECURITY','BUILDING_MANAGER','TECHNICIAN','COMPANY_SUPERVISOR','ADMIN')),
  building_id BIGINT REFERENCES buildings(id));

CREATE TABLE IF NOT EXISTS incidents (            -- [shared: Members 1 & 3]
  incident_id VARCHAR(30) PRIMARY KEY,
  elevator_id BIGINT NOT NULL REFERENCES elevators(id),
  reported_by BIGINT REFERENCES users(id),
  category VARCHAR(60) NOT NULL, description TEXT, is_emergency BOOLEAN NOT NULL DEFAULT FALSE,
  priority VARCHAR(2) NOT NULL DEFAULT 'P2' CHECK (priority IN ('P0','P1','P2','P3')),
  status VARCHAR(40) NOT NULL DEFAULT 'REPORTED',
  reported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  acknowledgement_due TIMESTAMPTZ, arrival_due TIMESTAMPTZ, resolution_due TIMESTAMPTZ,
  red_flag BOOLEAN NOT NULL DEFAULT FALSE, escalated_at TIMESTAMPTZ, escalation_reason TEXT,
  downtime_start TIMESTAMPTZ,
  acknowledged_at TIMESTAMPTZ, arrived_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ, verified_at TIMESTAMPTZ,  -- [M4]
  root_cause TEXT, resolution_summary TEXT, parts_replaced TEXT,                                          -- [M4]
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS idx_incidents_elevator ON incidents(elevator_id, reported_at);
CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);

CREATE TABLE IF NOT EXISTS assignments (          -- [shared: Members 2/3/4]
  id BIGSERIAL PRIMARY KEY, incident_id VARCHAR(30) NOT NULL REFERENCES incidents(incident_id),
  technician_id BIGINT NOT NULL REFERENCES users(id), assigned_by BIGINT REFERENCES users(id),
  status VARCHAR(40) NOT NULL DEFAULT 'ASSIGNED', active BOOLEAN NOT NULL DEFAULT TRUE,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  acknowledged_at TIMESTAMPTZ, arrived_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ,
  parts_delay_reason TEXT, parts_eta TIMESTAMPTZ);                                                        -- [M4] columns
CREATE UNIQUE INDEX IF NOT EXISTS uq_assignment_active ON assignments(incident_id) WHERE active;

CREATE TABLE IF NOT EXISTS incident_events (      -- [shared] every status change is timestamped
  id BIGSERIAL PRIMARY KEY, incident_id VARCHAR(30) NOT NULL REFERENCES incidents(incident_id),
  event_type VARCHAR(40) NOT NULL, from_status VARCHAR(40), to_status VARCHAR(40),
  actor_id BIGINT REFERENCES users(id), note TEXT, payload JSONB,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),   -- when it really happened (may be earlier if the phone was offline)
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  client_action_id UUID UNIQUE,                     -- [M4] idempotency key for offline replays / sync retries
  origin VARCHAR(20) NOT NULL DEFAULT 'BUILDING',   -- [M4] BUILDING | TECHNICIAN | COMPANY
  synced BOOLEAN NOT NULL DEFAULT FALSE);           -- [M4]
CREATE INDEX IF NOT EXISTS idx_events_incident ON incident_events(incident_id, occurred_at);

CREATE TABLE IF NOT EXISTS notification_outbox (  -- [shared: Member 2 owns the queue; Members 3/4 write + dispatch]
  id BIGSERIAL PRIMARY KEY, incident_id VARCHAR(30) REFERENCES incidents(incident_id),
  kind VARCHAR(20) NOT NULL DEFAULT 'UPDATE',       -- ESCALATION | ASSIGNMENT | UPDATE
  channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
  recipient VARCHAR(160) NOT NULL, subject VARCHAR(200), body TEXT NOT NULL,
  status VARCHAR(15) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','RETRY','SENT','DELIVERED','FAILED','ACKNOWLEDGED')),
  attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_error TEXT,
  provider_message_id VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), sent_at TIMESTAMPTZ, delivered_at TIMESTAMPTZ, acknowledged_at TIMESTAMPTZ);
CREATE INDEX IF NOT EXISTS idx_outbox_due ON notification_outbox(status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_provider ON notification_outbox(provider_message_id);
