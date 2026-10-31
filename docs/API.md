# Member 4 API (base path `/api`)

All responses use the team envelope: `{ "success": true|false, "data": ..., "error": {"code","message"}, "timestamp": "..." }`.
Dev identity headers (until Member 1's JWT is merged): `X-User-Id`, `X-User-Role`.
Errors: 400 `VALIDATION_ERROR`, 401 `UNAUTHENTICATED`, 403 `FORBIDDEN`/`NOT_YOUR_JOB`, 404 not found, 409 `INVALID_TRANSITION`.

## Incident statuses used (shared lifecycle)
`REPORTED → ASSIGNED → ACKNOWLEDGED → TECHNICIAN_ARRIVED → IN_PROGRESS ⇄ PARTS_DELAYED → RESOLVED_PENDING_VERIFICATION → VERIFIED_RESOLVED`
"Escalated / red flag" is a flag (`escalated_at`, `red_flag`), not a status. Resolved is not closed: only verification closes an incident.

## Technician (role TECHNICIAN)
| Method | Path | Notes |
|---|---|---|
| GET | `/technician/jobs?includeResolved=false` | My active assignments, red-flagged first |
| GET | `/technician/jobs/{incidentId}/timeline` | Timestamped events |
| POST | `/technician/jobs/{incidentId}/acknowledge` | ASSIGNED → ACKNOWLEDGED |
| POST | `/technician/jobs/{incidentId}/arrive` | ASSIGNED/ACKNOWLEDGED → TECHNICIAN_ARRIVED |
| POST | `/technician/jobs/{incidentId}/progress` | body `note` (required) |
| POST | `/technician/jobs/{incidentId}/parts-delay` | body `partsDelayReason` (required), `partsEta` |
| POST | `/technician/jobs/{incidentId}/resolve` | body `rootCause`, `actionTaken` (required), `partsReplaced` |

Optional on every POST: `clientActionId` (UUID, idempotency; repeats return `duplicate:true`) and `occurredAt` (real action time, for offline replay; future times are clamped to now).

## Company / supervisor
| Method | Path | Roles |
|---|---|---|
| GET | `/company/escalations`, `/company/overdue`, `/company/technicians` | supervisor, manager, admin |
| POST | `/company/incidents/{id}/assign` `{technicianId}` | supervisor, admin |
| POST | `/company/incidents/{id}/verify` `{approved, note}` | supervisor, manager, admin |

## Notifications
| Method | Path | Notes |
|---|---|---|
| GET | `/notifications?incidentId=` | Outbox rows with status |
| POST | `/notifications/webhook/{provider}` | Header `X-Webhook-Secret`; body `{providerMessageId, status: delivered\|failed\|acknowledged}`. **Only way a message becomes DELIVERED.** |
| POST | `/notifications/{id}/acknowledge` | Supervisor confirms they saw it |

Outbox statuses: `PENDING → SENT → DELIVERED → ACKNOWLEDGED`, or `RETRY` (exponential back-off) → `FAILED`.

## Analytics (supervisor, manager, admin)
`GET /analytics/summary?days=30` · `/volume` · `/by-priority` · `/repeat-faults?days=30&threshold=3` · `/elevators` · `/elevators/{id}/history` · `/report?days=30` (CSV)

## Sync
`GET /sync/status`. When `SYNC_ENABLED=true`, unsynced technician/company events are POSTed to `{BUILDING_SERVER_URL}/api/sync/events`
as `{ "events": [ {id, incidentId, eventType, fromStatus, toStatus, actorId, note, occurredAt, clientActionId, origin} ] }`.
Member 2's endpoint must de-duplicate on `clientActionId` (or `id`) so retries never create duplicates.

## What Member 4 needs from the others
- **Member 3** inserts escalation rows into `notification_outbox` (`kind='ESCALATION'`) and sets `red_flag`, `escalated_at`, `escalation_reason`, deadlines on `incidents`.
- **Member 2** owns the outbox table and `/api/sync/events`; this module only reads/dispatches the rows.
- **Member 1** supplies login/JWT and the elevator/building tables; swap the `X-User-*` headers for the real principal.
