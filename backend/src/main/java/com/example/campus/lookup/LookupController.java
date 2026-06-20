package com.example.campus.lookup;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.Db;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查、活动类型和积分规则等公共查询接口。
 */
@RestController
@RequestMapping("/api")
public class LookupController {
    private final Db db;

    /**
     * 注入查询基础数据使用的数据库工具类。
     */
    public LookupController(Db db) {
        this.db = db;
    }

    /**
     * 用于确认后端服务是否存活的轻量接口。
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    /**
     * 返回活动表单和筛选器使用的全部活动分类。
     */
    @GetMapping("/activity-types")
    public ApiResponse<Object> types() {
        return ApiResponse.ok(db.jdbc().queryForList("select * from activity_type order by type_id"));
    }

    /**
     * 返回积分规则及其对应的活动类型名称。
     */
    @GetMapping("/score-rules")
    public ApiResponse<Object> rules() {
        return ApiResponse.ok(db.jdbc().queryForList("""
                select sr.*, at.type_name
                from score_rule sr join activity_type at on sr.type_id = at.type_id
                order by sr.rule_id
                """));
    }
}
