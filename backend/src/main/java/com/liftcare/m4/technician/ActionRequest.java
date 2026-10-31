package com.liftcare.m4.technician;

import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ActionRequest(
    UUID clientActionId,            // idempotency key: replaying the same id is a no-op
    OffsetDateTime occurredAt,      // when the technician really did it (set by the app when queued offline)
    @Size(max = 2000) String note,
    @Size(max = 500) String partsDelayReason, OffsetDateTime partsEta,
    @Size(max = 1000) String rootCause, @Size(max = 2000) String actionTaken, @Size(max = 1000) String partsReplaced) {}
