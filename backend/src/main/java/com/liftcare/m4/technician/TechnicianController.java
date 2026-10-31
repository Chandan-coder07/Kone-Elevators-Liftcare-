package com.liftcare.m4.technician;

import com.liftcare.m4.common.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/technician")
public class TechnicianController {
    private final TechnicianService svc;
    public TechnicianController(TechnicianService svc) { this.svc = svc; }

    @GetMapping("/jobs")
    public ApiResponse<List<Map<String, Object>>> jobs(@RequestHeader("X-User-Id") long uid, @RequestHeader("X-User-Role") String role,
                                                       @RequestParam(defaultValue = "false") boolean includeResolved) {
        Auth.require(role, "TECHNICIAN");
        return ApiResponse.ok(svc.myJobs(uid, includeResolved));
    }

    @GetMapping("/jobs/{incidentId}/timeline")
    public ApiResponse<List<Map<String, Object>>> timeline(@RequestHeader("X-User-Role") String role, @PathVariable String incidentId) {
        Auth.require(role, "TECHNICIAN", "COMPANY_SUPERVISOR", "BUILDING_MANAGER", "ADMIN");
        return ApiResponse.ok(svc.timeline(incidentId));
    }

    /** action = acknowledge | arrive | progress | parts-delay | resolve */
    @PostMapping("/jobs/{incidentId}/{action}")
    public ApiResponse<Map<String, Object>> act(@RequestHeader("X-User-Id") long uid, @RequestHeader("X-User-Role") String role,
                                                @PathVariable String incidentId, @PathVariable String action,
                                                @Valid @RequestBody(required = false) ActionRequest body) {
        Auth.require(role, "TECHNICIAN");
        return ApiResponse.ok(svc.apply(uid, incidentId, Action.fromPath(action), body == null ? new ActionRequest(null, null, null, null, null, null, null, null) : body));
    }
}
