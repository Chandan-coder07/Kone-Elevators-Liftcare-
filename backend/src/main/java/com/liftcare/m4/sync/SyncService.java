package com.liftcare.m4.sync;

import com.liftcare.m4.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

/**
 * Pushes technician/company events back to the building server whenever it is reachable.
 * The building server (Member 2) must treat client_action_id / event id as an idempotency key so retries never duplicate.
 * Contract: POST {building-server-url}/api/sync/events  body { "events": [ {id, incidentId, eventType, fromStatus, toStatus, actorId, note, occurredAt, clientActionId, origin} ] }  -> 2xx
 */
@Component
@RestController
@RequestMapping("/api/sync")
public class SyncService {
    private final NamedParameterJdbcTemplate jdbc;
    @Value("${liftcare.sync.enabled}") boolean enabled;
    @Value("${liftcare.sync.building-server-url}") String url;
    private volatile Instant lastSuccess; private volatile String lastError;
    public SyncService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${liftcare.sync.interval-ms}")
    public void push() {
        if (!enabled) return;
        var pending = jdbc.queryForList("""
            SELECT id, incident_id AS "incidentId", event_type AS "eventType", from_status AS "fromStatus", to_status AS "toStatus",
                   actor_id AS "actorId", note, occurred_at AS "occurredAt", client_action_id AS "clientActionId", origin
            FROM incident_events WHERE NOT synced AND origin IN ('TECHNICIAN','COMPANY') ORDER BY id LIMIT 50""", Map.of());
        if (pending.isEmpty()) { lastSuccess = Instant.now(); return; }
        try {
            RestClient.create().post().uri(url + "/api/sync/events").body(Map.of("events", pending)).retrieve().toBodilessEntity();
            jdbc.update("UPDATE incident_events SET synced=true WHERE id IN (:ids)", Map.of("ids", pending.stream().map(e -> e.get("id")).toList()));
            lastSuccess = Instant.now(); lastError = null;
        } catch (Exception e) { lastError = e.getMessage(); }   // unreachable: stays unsynced, retried next tick
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Integer pending = enabled ? jdbc.queryForObject("SELECT count(*) FROM incident_events WHERE NOT synced AND origin IN ('TECHNICIAN','COMPANY')", Map.of(), Integer.class) : 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled); m.put("pendingEvents", pending); m.put("lastSuccessAt", lastSuccess); m.put("lastError", lastError);
        return ApiResponse.ok(m);
    }
}
