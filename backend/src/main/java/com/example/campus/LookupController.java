package com.example.campus;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LookupController {
    private final Db db;

    public LookupController(Db db) {
        this.db = db;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    @GetMapping("/activity-types")
    public ApiResponse<Object> types() {
        return ApiResponse.ok(db.jdbc().queryForList("select * from activity_type order by type_id"));
    }

    @GetMapping("/score-rules")
    public ApiResponse<Object> rules() {
        return ApiResponse.ok(db.jdbc().queryForList("""
                select sr.*, at.type_name
                from score_rule sr join activity_type at on sr.type_id = at.type_id
                order by sr.rule_id
                """));
    }
}
