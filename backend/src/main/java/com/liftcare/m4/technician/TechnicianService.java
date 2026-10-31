package com.liftcare.m4.technician;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.m4.common.ApiException;
import com.liftcare.m4.notification.Outbox;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class TechnicianService {
    private final NamedParameterJdbcTemplate jdbc; private final ObjectMapper json; private final Outbox outbox;
    public TechnicianService(NamedParameterJdbcTemplate jdbc, ObjectMapper json, Outbox outbox) { this.jdbc = jdbc; this.json = json; this.outbox = outbox; }

    public List<Map<String, Object>> myJobs(long techId, boolean includeResolved) {
        return jdbc.queryForList("""
            SELECT i.incident_id, i.category, i.description, i.priority, i.status, i.is_emergency, i.red_flag,
                   i.acknowledgement_due, i.arrival_due, i.resolution_due, i.reported_at,
                   e.id AS elevator_id, e.label AS elevator_label, b.name AS building_name,
                   a.assigned_at, a.parts_delay_reason, a.parts_eta
            FROM assignments a
            JOIN incidents i ON i.incident_id = a.incident_id
            JOIN elevators e ON e.id = i.elevator_id JOIN buildings b ON b.id = e.building_id
            WHERE a.technician_id = :t AND a.active
              AND (:all OR i.status NOT IN ('RESOLVED_PENDING_VERIFICATION','VERIFIED_RESOLVED'))
            ORDER BY i.red_flag DESC, i.priority, i.resolution_due NULLS LAST""",
            Map.of("t", techId, "all", includeResolved));
    }

    public List<Map<String, Object>> timeline(String incidentId) {
        return jdbc.queryForList("""
            SELECT ev.event_type, ev.from_status, ev.to_status, ev.note, ev.occurred_at, u.name AS actor
            FROM incident_events ev LEFT JOIN users u ON u.id = ev.actor_id
            WHERE ev.incident_id=:i ORDER BY ev.occurred_at, ev.id""", Map.of("i", incidentId));
    }

    @Transactional
    public Map<String, Object> apply(long techId, String incidentId, Action action, ActionRequest r) {
        if (r.clientActionId() != null && jdbc.queryForObject("SELECT count(*) FROM incident_events WHERE client_action_id=:c",
                Map.of("c", r.clientActionId()), Integer.class) > 0)
            return Map.of("incidentId", incidentId, "duplicate", true);   // already applied: safe retry

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                SELECT i.status, i.priority, a.id AS aid, a.technician_id FROM incidents i
                JOIN assignments a ON a.incident_id = i.incident_id AND a.active
                WHERE i.incident_id=:i FOR UPDATE OF i""", Map.of("i", incidentId));
        } catch (EmptyResultDataAccessException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_ASSIGNED", "No active assignment for " + incidentId);
        }
        if (((Number) row.get("technician_id")).longValue() != techId)
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_JOB", "This job is assigned to another technician");
        String from = (String) row.get("status");
        if (!action.from.contains(from))
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Cannot " + action.path + " while status is " + from);

        require(action == Action.PROGRESS, r.note(), "note");
        if (action == Action.PARTS_DELAY) { require(true, r.partsDelayReason(), "partsDelayReason"); }
        if (action == Action.RESOLVE) { require(true, r.rootCause(), "rootCause"); require(true, r.actionTaken(), "actionTaken"); }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime t = (r.occurredAt() == null || r.occurredAt().isAfter(now)) ? now : r.occurredAt();
        var p = new MapSqlParameterSource().addValue("i", incidentId).addValue("aid", row.get("aid")).addValue("s", action.to).addValue("t", t)
            .addValue("reason", r.partsDelayReason()).addValue("eta", r.partsEta())
            .addValue("rc", r.rootCause()).addValue("act", r.actionTaken()).addValue("pr", r.partsReplaced());

        String inc = "", asg = "";
        switch (action) {
            case ACKNOWLEDGE -> { inc = ", acknowledged_at=COALESCE(acknowledged_at,:t)"; asg = inc; }
            case ARRIVE -> { inc = ", acknowledged_at=COALESCE(acknowledged_at,:t), arrived_at=COALESCE(arrived_at,:t)"; asg = inc; }
            case PARTS_DELAY -> asg = ", parts_delay_reason=:reason, parts_eta=CAST(:eta AS timestamptz)";
            case RESOLVE -> { inc = ", resolved_at=:t, root_cause=:rc, resolution_summary=:act, parts_replaced=:pr"; asg = ", resolved_at=:t"; }
            default -> {}
        }
        jdbc.update("UPDATE incidents SET status=:s, updated_at=now()" + inc + " WHERE incident_id=:i", p);
        jdbc.update("UPDATE assignments SET status=:s" + asg + " WHERE id=:aid", p);

        String payload;
        try { payload = json.writeValueAsString(r); } catch (Exception e) { payload = "{}"; }
        jdbc.update("""
            INSERT INTO incident_events(incident_id,event_type,from_status,to_status,actor_id,note,payload,occurred_at,client_action_id,origin)
            VALUES (:i,:et,:f,:s,:a,:n,CAST(:pl AS jsonb),:t,:c,'TECHNICIAN')""",
            p.addValue("et", "TECH_" + action.name()).addValue("f", from).addValue("a", techId)
             .addValue("n", firstNonBlank(r.note(), r.partsDelayReason(), r.actionTaken())).addValue("pl", payload).addValue("c", r.clientActionId()));

        if (action == Action.PARTS_DELAY)
            outbox.enqueueForRoles(incidentId, "UPDATE", List.of("COMPANY_SUPERVISOR", "BUILDING_MANAGER"),
                "Parts delay on " + incidentId, "Technician reports a parts delay: " + r.partsDelayReason() + (r.partsEta() != null ? " (ETA " + r.partsEta() + ")" : ""));
        if (action == Action.RESOLVE)
            outbox.enqueueForRoles(incidentId, "UPDATE", List.of("COMPANY_SUPERVISOR", "BUILDING_MANAGER"),
                incidentId + " repaired - needs verification", "Technician marked the fault repaired: " + r.actionTaken() + ". Please verify.");
        return Map.of("incidentId", incidentId, "status", action.to, "occurredAt", t, "duplicate", false);
    }

    private static void require(boolean cond, String v, String field) {
        if (cond && (v == null || v.isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", field + " is required");
    }
    private static String firstNonBlank(String... v) { for (String s : v) if (s != null && !s.isBlank()) return s; return null; }
}
