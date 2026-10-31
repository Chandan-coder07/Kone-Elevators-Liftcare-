package com.liftcare.m4.notification;

import com.liftcare.m4.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NamedParameterJdbcTemplate jdbc;
    @Value("${liftcare.notifications.webhook-secret}") String secret;
    public NotificationController(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestHeader("X-User-Role") String role,
                                                       @RequestParam(required = false) String incidentId) {
        Auth.require(role, Auth.VIEW_ALL);
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT id, incident_id, kind, channel, recipient, subject, status, attempts, last_error,
                   created_at, sent_at, delivered_at, acknowledged_at
            FROM notification_outbox WHERE (CAST(:i AS VARCHAR) IS NULL OR incident_id=:i) ORDER BY created_at DESC LIMIT 200""",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("i", incidentId)));
    }

    /** Provider callback. The ONLY place a message becomes DELIVERED. Body: {providerMessageId, status: delivered|failed|acknowledged}. */
    public record Callback(String providerMessageId, String status) {}
    @PostMapping("/webhook/{provider}")
    public ApiResponse<Map<String, Object>> webhook(@PathVariable String provider,
                                                    @RequestHeader(value = "X-Webhook-Secret", required = false) String given,
                                                    @RequestBody Callback cb) {
        if (!secret.equals(given)) throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_WEBHOOK_SECRET", "Invalid webhook secret");
        String sql = switch (String.valueOf(cb.status()).toLowerCase()) {
            case "delivered" -> "UPDATE notification_outbox SET status='DELIVERED', delivered_at=now() WHERE provider_message_id=:p AND status IN ('SENT','DELIVERED')";
            case "failed" -> "UPDATE notification_outbox SET status='FAILED', last_error='Provider reported failure' WHERE provider_message_id=:p AND status<>'ACKNOWLEDGED'";
            case "acknowledged" -> "UPDATE notification_outbox SET status='ACKNOWLEDGED', acknowledged_at=now(), delivered_at=COALESCE(delivered_at,now()) WHERE provider_message_id=:p";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_STATUS", "status must be delivered, failed or acknowledged");
        };
        int n = jdbc.update(sql, Map.of("p", String.valueOf(cb.providerMessageId())));
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "No matching message in a state that allows this update");
        return ApiResponse.ok(Map.of("updated", n));
    }

    /** A supervisor confirms they have seen an escalation (for channels with no provider receipts). */
    @PostMapping("/{id}/acknowledge")
    public ApiResponse<Map<String, Object>> acknowledge(@RequestHeader("X-User-Role") String role, @PathVariable long id) {
        Auth.require(role, Auth.SERVICE);
        int n = jdbc.update("UPDATE notification_outbox SET status='ACKNOWLEDGED', acknowledged_at=now() WHERE id=:id AND status IN ('SENT','DELIVERED')", Map.of("id", id));
        if (n == 0) throw new ApiException(HttpStatus.CONFLICT, "NOT_ACKNOWLEDGEABLE", "Only sent or delivered messages can be acknowledged");
        return ApiResponse.ok(Map.of("id", id, "status", "ACKNOWLEDGED"));
    }
}
