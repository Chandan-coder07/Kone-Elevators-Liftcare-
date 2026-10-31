package com.liftcare.m4.notification;

public interface NotificationChannel {
    String type();   // EMAIL | SMS | PUSH
    /** accepted=true means the provider took the message. It does NOT mean delivered; only a provider webhook may say that. */
    SendResult send(OutboxMessage m);

    record OutboxMessage(long id, String incidentId, String recipient, String subject, String body) {}
    record SendResult(boolean accepted, String providerMessageId, String error) {
        public static SendResult accepted(String id) { return new SendResult(true, id, null); }
        public static SendResult failed(String why) { return new SendResult(false, null, why); }
    }
}
