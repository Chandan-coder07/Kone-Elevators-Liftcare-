package com.liftcare.m4.company;

import com.liftcare.m4.common.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/company")
public class CompanyController {
    private final CompanyService svc;
    public CompanyController(CompanyService svc) { this.svc = svc; }

    @GetMapping("/escalations") public ApiResponse<List<Map<String, Object>>> escalations(@RequestHeader("X-User-Role") String role) { Auth.require(role, Auth.VIEW_ALL); return ApiResponse.ok(svc.escalations()); }
    @GetMapping("/overdue") public ApiResponse<List<Map<String, Object>>> overdue(@RequestHeader("X-User-Role") String role) { Auth.require(role, Auth.VIEW_ALL); return ApiResponse.ok(svc.overdue()); }
    @GetMapping("/technicians") public ApiResponse<List<Map<String, Object>>> technicians(@RequestHeader("X-User-Role") String role) { Auth.require(role, Auth.VIEW_ALL); return ApiResponse.ok(svc.technicians()); }

    public record AssignBody(@NotNull Long technicianId) {}
    @PostMapping("/incidents/{id}/assign")
    public ApiResponse<Map<String, Object>> assign(@RequestHeader("X-User-Id") long uid, @RequestHeader("X-User-Role") String role,
                                                   @PathVariable String id, @RequestBody @jakarta.validation.Valid AssignBody b) {
        Auth.require(role, Auth.SERVICE);
        return ApiResponse.ok(svc.assign(uid, id, b.technicianId()));
    }

    public record VerifyBody(boolean approved, String note) {}
    @PostMapping("/incidents/{id}/verify")
    public ApiResponse<Map<String, Object>> verify(@RequestHeader("X-User-Id") long uid, @RequestHeader("X-User-Role") String role,
                                                   @PathVariable String id, @RequestBody VerifyBody b) {
        Auth.require(role, "COMPANY_SUPERVISOR", "BUILDING_MANAGER", "ADMIN");
        return ApiResponse.ok(svc.verify(uid, id, b.approved(), b.note()));
    }
}
