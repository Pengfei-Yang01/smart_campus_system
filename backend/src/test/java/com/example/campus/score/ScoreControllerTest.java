package com.example.campus.score;

import com.example.campus.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 积分模块约束测试。
 *
 * 覆盖场景:
 * - 查询当前用户积分统计
 * - 查询积分详情
 * - 查询积分规则列表
 * - 为已结束活动录入积分成功
 * - 为未结束活动录入积分被拒绝
 * - 管理员审核积分记录
 * - 管理员维护积分规则
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ScoreControllerTest {

    @Autowired
    private TestRestTemplate rest;

    // ======================== 查询积分 ========================

    /** 当前用户只能统计自己已经审核通过的积分记录。 */
    @Test
    @Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void summary_统计当前用户已通过积分() {
        var token = loginAs("student2");
        var resp = get("/api/scores/summary", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(((Number) data.get("totalRecords")).intValue()).isEqualTo(1);
        assertThat(((Number) data.get("totalScore")).doubleValue()).isEqualTo(2.6);
        assertThat(((Number) data.get("averageScore")).doubleValue()).isEqualTo(2.6);
    }

    /** 积分详情接口应返回学生、活动和组织等展示字段。 */
    @Test
    @Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void detail_查询积分详情() {
        var token = loginAs("admin");
        var resp = get("/api/scores/1", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(((Number) data.get("score_id")).longValue()).isEqualTo(1L);
        assertThat(data).containsKeys("real_name", "student_no", "activity_name", "org_name");
    }

    /** 积分规则查询接口应返回初始化脚本中的规则数据。 */
    @Test
    @Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void rules_查询积分规则列表() {
        var token = loginAs("student1");
        var resp = get("/api/scores/rules", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Map<String, Object>>) resp.getBody().data();
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0)).containsKeys("rule_id", "type_id", "base_score", "effective_status");
    }

    // ======================== 录入积分 ========================

    /** 组织负责人可以为已结束活动中有效报名的学生录入积分 */
    @Test
    @Sql(statements = {
            "insert into registration(registration_id, activity_id, user_id, registration_status, checkin_status)"
                    + " values(100, 2, 2, 'VALID', 'NOT_CHECKED')"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void record_已结束活动录分成功() {
        var token = loginAs("leader2");
        // 活动2（FINISHED, 组织2）中学生1（userId=2）已报名，
        // 学生3已有积分记录会触发唯一键冲突，所以用学生1测试
        var body = Map.of("activityId", 2, "userId", 2);
        var resp = post("/api/scores", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        // finalScore = base_score * weight = 2.00 * 1.00(普通) = 2.00
        assertThat(data).containsKey("finalScore");
        assertThat(data).containsKey("scoreId");
    }

    /** 为未结束活动录入积分应被拒绝 */
    @Test
    void record_未结束活动录分失败() {
        var token = loginAs("leader1");
        // 活动1 状态为 OPEN（未结束）
        var body = Map.of("activityId", 1, "userId", 2);
        var resp = post("/api/scores", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("活动结束后才能录入积分");
    }

    // ======================== 审核积分 ========================

    /** 管理员可以审核积分记录，审核结果会返回给前端。 */
    @Test
    @Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(statements = "update score_record set audit_status='PENDING', reviewer_id=null, reviewed_at=null where score_id=1",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void audit_管理员审核积分记录() {
        var token = loginAs("admin");
        var resp = patch("/api/scores/1/audit",
                Map.of("auditStatus", "REJECTED", "rejectReason", "测试驳回"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsEntry("auditStatus", "REJECTED");
    }

    // ======================== 积分规则维护 ========================

    /** 管理员可以创建停用状态的备用积分规则。 */
    @Test
    @Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void rule_管理员创建备用规则() {
        var token = loginAs("admin");
        var body = Map.<String, Object>of(
                "typeId", 1,
                "baseScore", 1.5,
                "normalWeight", 1.0,
                "memberWeight", 1.2,
                "leaderWeight", 1.5,
                "ruleDesc", "测试备用规则",
                "effectiveStatus", "DISABLED"
        );
        var resp = post("/api/scores/rules", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsKey("ruleId");
    }

    /** 管理员可以更新已有积分规则的分值、权重和启用状态。 */
    @Test
    @Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void rule_管理员更新规则() {
        var token = loginAs("admin");
        var body = Map.<String, Object>of(
                "baseScore", 1.2,
                "normalWeight", 1.1,
                "memberWeight", 1.3,
                "leaderWeight", 1.6,
                "ruleDesc", "测试更新规则",
                "effectiveStatus", "ENABLED"
        );
        var resp = put("/api/scores/rules/1", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    // ======================== 辅助方法 ========================

    private String loginAs(String username) {
        var body = Map.of("username", username, "password", "123456");
        var resp = rest.postForEntity("/api/auth/login", body, ApiResponse.class);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        return (String) data.get("token");
    }

    private HttpHeaders bearerHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<ApiResponse> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> post(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> patch(String path, Object body, String token) {
        var headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> put(String path, Object body, String token) {
        var headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }
}
