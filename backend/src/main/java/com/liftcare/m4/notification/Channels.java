package com.liftcare.m4.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.UUID;

public final class Channels {
    private Channels() {}

    @Component
    public static class Email implements NotificationChannel {
        private final JavaMailSender mail;
        @Value("${liftcare.notifications.email.enabled}") boolean enabled;
        @Value("${liftcare.notifications.email.from}") String from;
        public Email(JavaMailSender mail) { this.mail = mail; }
        public String type() { return "EMAIL"; }
        public SendResult send(OutboxMessage m) {
            if (!enabled) return SendResult.failed("Email channel disabled");
            try {
                var msg = new SimpleMailMessage();
                msg.setFrom(from); msg.setTo(m.recipient()); msg.setSubject(m.subject()); msg.setText(m.body());
                mail.send(msg);
                // SMTP acceptance is not delivery. Stays SENT until a bounce/receipt webhook says otherwise.
                return SendResult.accepted("smtp-" + UUID.randomUUID());
            } catch (Exception e) { return SendResult.failed(e.getMessage()); }
        }
    }

    /** Generic JSON-over-HTTP provider (SMS gateway or push service). POST {to,subject,message,reference}. */
    abstract static class HttpChannel implements NotificationChannel {
        private final String type; private final boolean enabled; private final String url; private final String apiKey;
        HttpChannel(String type, boolean enabled, String url, String apiKey) { this.type = type; this.enabled = enabled; this.url = url; this.apiKey = apiKey; }
        public String type() { return type; }
        @SuppressWarnings("unchecked")
        public SendResult send(OutboxMessage m) {
            if (!enabled || url == null || url.isBlank()) return SendResult.failed(type + " integration not configured");
            try {
                Map<String, Object> res = RestClient.create().post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("to", m.recipient(), "subject", String.valueOf(m.subject()), "message", m.body(), "reference", m.id()))
                    .retrieve().body(Map.class);
                Object id = res == null ? null : res.get("id");
                return id == null ? SendResult.failed("Provider returned no message id") : SendResult.accepted(id.toString());
            } catch (Exception e) { return SendResult.failed(e.getMessage()); }
        }
    }

    @Component
    public static class Sms extends HttpChannel {
        public Sms(@Value("${liftcare.notifications.sms.enabled}") boolean e, @Value("${liftcare.notifications.sms.url}") String u,
                   @Value("${liftcare.notifications.sms.api-key}") String k) { super("SMS", e, u, k); }
    }

    @Component
    public static class Push extends HttpChannel {
        public Push(@Value("${liftcare.notifications.push.enabled}") boolean e, @Value("${liftcare.notifications.push.url}") String u,
                    @Value("${liftcare.notifications.push.api-key}") String k) { super("PUSH", e, u, k); }
    }
}
