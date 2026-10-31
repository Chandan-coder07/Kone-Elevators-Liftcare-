package com.liftcare.m4.technician;

import com.liftcare.m4.common.ApiException;
import org.springframework.http.HttpStatus;
import java.util.Set;

/** The technician lifecycle. Shared incident statuses - keep identical across modules. */
public enum Action {
    ACKNOWLEDGE("acknowledge", Set.of("ASSIGNED"), "ACKNOWLEDGED"),
    // A technician may arrive without acknowledging first (critical call-outs must not be blocked by paperwork).
    ARRIVE("arrive", Set.of("ASSIGNED", "ACKNOWLEDGED"), "TECHNICIAN_ARRIVED"),
    PROGRESS("progress", Set.of("TECHNICIAN_ARRIVED", "IN_PROGRESS", "PARTS_DELAYED"), "IN_PROGRESS"),
    PARTS_DELAY("parts-delay", Set.of("TECHNICIAN_ARRIVED", "IN_PROGRESS"), "PARTS_DELAYED"),
    // Resolving is NOT closing: the incident waits for verification and stays tracked until then.
    RESOLVE("resolve", Set.of("TECHNICIAN_ARRIVED", "IN_PROGRESS", "PARTS_DELAYED"), "RESOLVED_PENDING_VERIFICATION");

    public final String path; public final Set<String> from; public final String to;
    Action(String path, Set<String> from, String to) { this.path = path; this.from = from; this.to = to; }

    public static Action fromPath(String p) {
        for (Action a : values()) if (a.path.equals(p)) return a;
        throw new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_ACTION", "Unknown action: " + p);
    }
}
