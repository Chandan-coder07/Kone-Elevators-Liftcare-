package com.liftcare.m4.notification;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** Writes to the shared notification_outbox (Member 2's durable queue). Nothing here marks anything delivered. */
@Component
public class Outbox {
    private final NamedParameterJdbcTemplate jdbc;
    public Outbox(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Queue EMAIL for every user holding one of the roles; add SMS for P0/P1 incidents. */
    public void enqueueForRoles(String incidentId, String kind, List<String> roles, String subject, String body) {
        String priority = jdbc.queryForObject("SELECT priority FROM incidents WHERE incident_id=:i", Map.of("i", incidentId), String.class);
        boolean urgent = "P0".equals(priority) || "P1".equals(priority);
        var users = jdbc.queryForList("SELECT email, phone FROM users WHERE role IN (:r)", Map.of("r", roles));
        for (var u : users) {
            if (u.get("email") != null) insert(incidentId, kind, "EMAIL", (String) u.get("email"), subject, body);
            if (urgent && u.get("phone") != null) insert(incidentId, kind, "SMS", (String) u.get("phone"), subject, body);
        }
    }

    public void enqueueForUser(String incidentId, String kind, long userId, String subject, String body) {
        var u = jdbc.queryForMap("SELECT email, phone FROM users WHERE id=:id", Map.of("id", userId));
        if (u.get("phone") != null) insert(incidentId, kind, "SMS", (String) u.get("phone"), subject, body);
        if (u.get("email") != null) insert(incidentId, kind, "EMAIL", (String) u.get("email"), subject, body);
    }

    private void insert(String incidentId, String kind, String channel, String recipient, String subject, String body) {
        jdbc.update("""
            INSERT INTO notification_outbox(incident_id,kind,channel,recipient,subject,body)
            VALUES (:i,:k,:c,:r,:s,:b)""",
            new MapSqlParameterSource().addValue("i", incidentId).addValue("k", kind).addValue("c", channel)
                .addValue("r", recipient).addValue("s", subject).addValue("b", body));
    }
}
