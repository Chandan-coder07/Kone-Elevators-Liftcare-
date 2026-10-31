package com.liftcare.m4.company;

import com.liftcare.m4.common.ApiException;
import com.liftcare.m4.notification.Outbox;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CompanyService {
    private final NamedParameterJdbcTemplate jdbc; private final Outbox outbox;
    public CompanyService(NamedParameterJdbcTemplate jdbc, Outbox outbox) { this.jdbc = jdbc; this.outbox = outbox; }

    private static final String BASE = """
        SELECT i.incident_id, i.category, i.priority, i.status, i.is_emergency, i.red_flag, i.reported_at, i.resolution_due,
               i.escalated_at, i.escalation_reason, i.resolved_at, e.label AS elevator_label, b.name AS building_name,
               a.technician_id, t.name AS technician_name,
               (SELECT n.status FROM notification_outbox n WHERE n.incident_id=i.incident_id AND n.kind='ESCALATION'
                ORDER BY n.created_at DESC LIMIT 1) AS escalation_notice_status
        FROM incidents i JOIN elevators e ON e.id=i.elevator_id JOIN buildings b ON b.id=e.building_id
        LEFT JOIN assignments a ON a.incident_id=i.incident_id AND a.active LEFT JOIN users t ON t.id=a.technician_id """;

    /** Escalated and not yet verified - this includes "resolved, awaiting verification". */
    public List<Map<String, Object>> escalations() {
        return jdbc.queryForList(BASE + " WHERE i.escalated_at IS NOT NULL AND i.status<>'VERIFIED_RESOLVED' ORDER BY i.priority, i.escalated_at", Map.of());
    }
    /** Past the resolution deadline (or red-flagged) and not verified. */
    public List<Map<String, Object>> overdue() {
        return jdbc.queryForList(BASE + " WHERE i.status<>'VERIFIED_RESOLVED' AND (i.red_flag OR i.resolution_due < now()) ORDER BY i.resolution_due", Map.of());
    }
    public List<Map<String, Object>> technicians() {
        return jdbc.queryForList("""
            SELECT u.id, u.name, u.phone, count(a.id) FILTER (WHERE i.status NOT IN ('RESOLVED_PENDING_VERIFICATION','VERIFIED_RESOLVED')) AS open_jobs
            FROM users u LEFT JOIN assignments a ON a.technician_id=u.id AND a.active
            LEFT JOIN incidents i ON i.incident_id=a.incident_id
            WHERE u.role='TECHNICIAN' GROUP BY u.id, u.name, u.phone ORDER BY u.name""", Map.of());
    }

    @Transactional
    public Map<String, Object> assign(long supervisorId, String incidentId, long technicianId) {
        Integer isTech = jdbc.queryForObject("SELECT count(*) FROM users WHERE id=:t AND role='TECHNICIAN'", Map.of("t", technicianId), Integer.class);
        if (isTech == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_TECHNICIAN", "User " + technicianId + " is not a technician");
        var cur = jdbc.queryForList("SELECT status FROM incidents WHERE incident_id=:i FOR UPDATE", Map.of("i", incidentId));
        if (cur.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "No incident " + incidentId);
        String from = (String) cur.get(0).get("status");
        if (from.equals("VERIFIED_RESOLVED")) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLOSED", "Incident is already closed");
        jdbc.update("UPDATE assignments SET active=false WHERE incident_id=:i AND active", Map.of("i", incidentId));
        jdbc.update("INSERT INTO assignments(incident_id,technician_id,assigned_by) VALUES (:i,:t,:s)", Map.of("i", incidentId, "t", technicianId, "s", supervisorId));
        jdbc.update("UPDATE incidents SET status='ASSIGNED', updated_at=now() WHERE incident_id=:i", Map.of("i", incidentId));
        jdbc.update("INSERT INTO incident_events(incident_id,event_type,from_status,to_status,actor_id,origin) VALUES (:i,'ASSIGNED',:f,'ASSIGNED',:s,'COMPANY')",
            Map.of("i", incidentId, "f", from, "s", supervisorId));
        outbox.enqueueForUser(incidentId, "ASSIGNMENT", technicianId, "New job " + incidentId, "You have been assigned " + incidentId + ". Open the LiftCare technician app.");
        return Map.of("incidentId", incidentId, "technicianId", technicianId, "status", "ASSIGNED");
    }

    /** Verify the repair (closes the incident, stops SLA tracking) or reject it (sends it back to the technician). */
    @Transactional
    public Map<String, Object> verify(long actorId, String incidentId, boolean approved, String note) {
        var cur = jdbc.queryForList("SELECT status FROM incidents WHERE incident_id=:i FOR UPDATE", Map.of("i", incidentId));
        if (cur.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "No incident " + incidentId);
        if (!"RESOLVED_PENDING_VERIFICATION".equals(cur.get(0).get("status")))
            throw new ApiException(HttpStatus.CONFLICT, "NOT_AWAITING_VERIFICATION", "Incident is not waiting for verification");
        String to = approved ? "VERIFIED_RESOLVED" : "IN_PROGRESS";
        jdbc.update("UPDATE incidents SET status=:s, updated_at=now()" + (approved ? ", verified_at=now()" : ", resolved_at=NULL") + " WHERE incident_id=:i",
            Map.of("s", to, "i", incidentId));
        jdbc.update("UPDATE assignments SET status=:s" + (approved ? "" : ", resolved_at=NULL") + " WHERE incident_id=:i AND active", Map.of("s", to, "i", incidentId));
        jdbc.update("INSERT INTO incident_events(incident_id,event_type,from_status,to_status,actor_id,note,origin) VALUES (:i,:e,'RESOLVED_PENDING_VERIFICATION',:s,:a,:n,'COMPANY')",
            new MapSqlParameterSource().addValue("i", incidentId).addValue("e", approved ? "VERIFIED" : "VERIFICATION_REJECTED").addValue("s", to).addValue("a", actorId).addValue("n", note));
        return Map.of("incidentId", incidentId, "status", to);
    }
}
