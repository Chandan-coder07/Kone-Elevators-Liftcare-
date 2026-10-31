package com.liftcare.m4.analytics;

import com.liftcare.m4.common.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final NamedParameterJdbcTemplate jdbc;
    public AnalyticsController(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String WINDOW = " i.reported_at >= now() - make_interval(days => :days)";

    /** Volume, downtime, response time, resolution time, SLA breaches. Times in minutes. */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestHeader("X-User-Role") String role, @RequestParam(defaultValue = "30") int days) {
        Auth.require(role, Auth.VIEW_ALL);
        return ApiResponse.ok(jdbc.queryForMap("""
            SELECT count(*) AS incidents,
              count(*) FILTER (WHERE i.status NOT IN ('VERIFIED_RESOLVED')) AS open_incidents,
              count(*) FILTER (WHERE i.red_flag) AS sla_breaches,
              round(100.0 * count(*) FILTER (WHERE NOT i.red_flag) / NULLIF(count(*),0), 1) AS sla_met_pct,
              round(avg(EXTRACT(EPOCH FROM (i.acknowledged_at - i.reported_at))/60)::numeric, 1) AS avg_response_min,
              round(avg(EXTRACT(EPOCH FROM (i.arrived_at - i.reported_at))/60)::numeric, 1) AS avg_arrival_min,
              round(avg(EXTRACT(EPOCH FROM (i.resolved_at - i.reported_at))/60)::numeric, 1) AS avg_resolution_min,
              round(coalesce(sum(EXTRACT(EPOCH FROM (coalesce(i.resolved_at, now()) - i.downtime_start))/60) FILTER (WHERE i.downtime_start IS NOT NULL),0)::numeric, 0) AS downtime_min
            FROM incidents i WHERE """ + WINDOW, Map.of("days", days)));
    }

    @GetMapping("/volume")
    public ApiResponse<List<Map<String, Object>>> volume(@RequestHeader("X-User-Role") String role, @RequestParam(defaultValue = "30") int days) {
        Auth.require(role, Auth.VIEW_ALL);
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT to_char(date_trunc('day', i.reported_at), 'YYYY-MM-DD') AS day, count(*) AS incidents,
                   count(*) FILTER (WHERE i.red_flag) AS breaches
            FROM incidents i WHERE """ + WINDOW + " GROUP BY 1 ORDER BY 1", Map.of("days", days)));
    }

    @GetMapping("/by-priority")
    public ApiResponse<List<Map<String, Object>>> byPriority(@RequestHeader("X-User-Role") String role, @RequestParam(defaultValue = "30") int days) {
        Auth.require(role, Auth.VIEW_ALL);
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT i.priority, count(*) AS incidents, count(*) FILTER (WHERE i.red_flag) AS breaches,
                   round(avg(EXTRACT(EPOCH FROM (i.acknowledged_at - i.reported_at))/60)::numeric,1) AS avg_response_min,
                   round(avg(EXTRACT(EPOCH FROM (i.resolved_at - i.reported_at))/60)::numeric,1) AS avg_resolution_min
            FROM incidents i WHERE """ + WINDOW + " GROUP BY i.priority ORDER BY i.priority", Map.of("days", days)));
    }

    /** Same elevator + same fault category, `threshold` or more times inside the window. */
    @GetMapping("/repeat-faults")
    public ApiResponse<List<Map<String, Object>>> repeats(@RequestHeader("X-User-Role") String role,
                                                          @RequestParam(defaultValue = "30") int days, @RequestParam(defaultValue = "3") int threshold) {
        Auth.require(role, Auth.VIEW_ALL);
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT e.id AS elevator_id, e.label AS elevator_label, b.name AS building_name, i.category,
                   count(*) AS occurrences, min(i.reported_at) AS first_seen, max(i.reported_at) AS last_seen
            FROM incidents i JOIN elevators e ON e.id=i.elevator_id JOIN buildings b ON b.id=e.building_id
            WHERE """ + WINDOW + """
             GROUP BY e.id, e.label, b.name, i.category HAVING count(*) >= :th ORDER BY occurrences DESC, last_seen DESC""",
            Map.of("days", days, "th", threshold)));
    }

    @GetMapping("/elevators")
    public ApiResponse<List<Map<String, Object>>> elevators(@RequestHeader("X-User-Role") String role) {
        Auth.require(role, Auth.VIEW_ALL);
        return ApiResponse.ok(jdbc.queryForList("SELECT e.id, e.label, e.model, b.name AS building_name FROM elevators e JOIN buildings b ON b.id=e.building_id ORDER BY b.name, e.label", Map.of()));
    }

    /** Service history for one elevator: every incident with technician, times, root cause and parts. */
    @GetMapping("/elevators/{id}/history")
    public ApiResponse<List<Map<String, Object>>> history(@RequestHeader("X-User-Role") String role, @PathVariable long id) {
        Auth.require(role, Auth.VIEW_ALL);
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT i.incident_id, i.category, i.priority, i.status, i.red_flag, i.reported_at, i.acknowledged_at, i.arrived_at, i.resolved_at, i.verified_at,
                   i.root_cause, i.resolution_summary, i.parts_replaced, t.name AS technician_name,
                   round((EXTRACT(EPOCH FROM (i.resolved_at - i.reported_at))/60)::numeric,0) AS resolution_min
            FROM incidents i LEFT JOIN assignments a ON a.incident_id=i.incident_id AND a.active LEFT JOIN users t ON t.id=a.technician_id
            WHERE i.elevator_id=:id ORDER BY i.reported_at DESC LIMIT 200""", Map.of("id", id)));
    }

    /** Performance report per elevator, downloadable as CSV. */
    @GetMapping(value = "/report", produces = "text/csv")
    public ResponseEntity<String> report(@RequestHeader("X-User-Role") String role, @RequestParam(defaultValue = "30") int days) {
        Auth.require(role, Auth.VIEW_ALL);
        var rows = jdbc.queryForList("""
            SELECT b.name AS building, e.label AS elevator, count(i.incident_id) AS incidents,
                   count(*) FILTER (WHERE i.red_flag) AS sla_breaches,
                   round(avg(EXTRACT(EPOCH FROM (i.acknowledged_at - i.reported_at))/60)::numeric,1) AS avg_response_min,
                   round(avg(EXTRACT(EPOCH FROM (i.resolved_at - i.reported_at))/60)::numeric,1) AS avg_resolution_min,
                   round(coalesce(sum(EXTRACT(EPOCH FROM (coalesce(i.resolved_at, now()) - i.downtime_start))/60) FILTER (WHERE i.downtime_start IS NOT NULL),0)::numeric,0) AS downtime_min
            FROM elevators e JOIN buildings b ON b.id=e.building_id
            LEFT JOIN incidents i ON i.elevator_id=e.id AND """ + WINDOW + " GROUP BY b.name, e.label ORDER BY b.name, e.label", Map.of("days", days));
        var sb = new StringBuilder("building,elevator,incidents,sla_breaches,avg_response_min,avg_resolution_min,downtime_min\n");
        for (var r : rows) {
            sb.append(String.join(",", List.of("building", "elevator", "incidents", "sla_breaches", "avg_response_min", "avg_resolution_min", "downtime_min").stream()
                .map(k -> r.get(k) == null ? "" : "\"" + r.get(k).toString().replace("\"", "\"\"") + "\"").toList())).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=liftcare-service-report.csv").body(sb.toString());
    }
}
