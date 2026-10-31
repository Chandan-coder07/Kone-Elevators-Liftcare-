package com.liftcare.m4.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Drains the durable outbox. Retries with exponential back-off. Never writes DELIVERED (see NotificationController webhook). */
@Component
public class NotificationDispatcher {
    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, NotificationChannel> channels;
    @Value("${liftcare.notifications.max-attempts}") int maxAttempts;

    public NotificationDispatcher(NamedParameterJdbcTemplate jdbc, List<NotificationChannel> list) {
        this.jdbc = jdbc;
        this.channels = list.stream().collect(Collectors.toMap(NotificationChannel::type, c -> c));
    }

    @Scheduled(fixedDelayString = "${liftcare.notifications.poll-ms}")
    @Transactional
    public void dispatchDue() {
        var due = jdbc.queryForList("""
            SELECT id, incident_id, channel, recipient, subject, body, attempts FROM notification_outbox
            WHERE status IN ('PENDING','RETRY') AND next_attempt_at <= now()
            ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED""", Map.of());
        for (var row : due) {
            long id = ((Number) row.get("id")).longValue();
            int attempts = ((Number) row.get("attempts")).intValue() + 1;
            var ch = channels.get((String) row.get("channel"));
            var res = ch == null ? NotificationChannel.SendResult.failed("Unknown channel")
                : ch.send(new NotificationChannel.OutboxMessage(id, (String) row.get("incident_id"),
                    (String) row.get("recipient"), (String) row.get("subject"), (String) row.get("body")));
            if (res.accepted()) {
                jdbc.update("UPDATE notification_outbox SET status='SENT', attempts=:a, sent_at=now(), provider_message_id=:p, last_error=NULL WHERE id=:id",
                    Map.of("a", attempts, "p", res.providerMessageId(), "id", id));
            } else if (attempts >= maxAttempts) {
                jdbc.update("UPDATE notification_outbox SET status='FAILED', attempts=:a, last_error=:e WHERE id=:id",
                    Map.of("a", attempts, "e", String.valueOf(res.error()), "id", id));
            } else {
                jdbc.update("UPDATE notification_outbox SET status='RETRY', attempts=:a, last_error=:e, next_attempt_at=now()+make_interval(mins => :m) WHERE id=:id",
                    Map.of("a", attempts, "e", String.valueOf(res.error()), "m", 1 << attempts, "id", id));
            }
        }
    }
}
