package com.example.campus.activity;

import com.example.campus.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活动模块约束测试。
 *
 * 覆盖场景:
 * - 负责人成功创建活动
 * - 学生创建活动被拒绝（角色权限约束）
 * - 活动状态有效流转 DRAFT→OPEN→CLOSED→FINISHED→OFFLINE
 * - 活动状态无效跳转被拒绝（DRAFT→FINISHED）
 * - 正常报名成功
 * - 活动已满员时报名被拒绝（容量约束）
 * - 报名截止后报名被拒绝（时间约束）
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ActivityControllerTest {

    @Autowired
    private TestRestTemplate rest;

    // ======================== 创建活动 ========================

    /** 组织负责人可以为自己负责的组织创建活动 */
    @Test
    void create_负责人成功创建() {
        var token = loginAs("leader1");
        var body = Map.<String, Object>of(
                "activityName", "测试新活动",
                "typeId", 1,
                "orgId", 1,
                "startTime", "2026-08-20 14:00:00",
                "endTime", "2026-08-20 17:00:00",
                "registrationDeadline", "2026-08-19 18:00:00",
                "location", "测试地点",
                "capacity", 30
        );
        var resp = post("/api/activities", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 普通学生不能创建活动（角色权限约束） */
    @Test
    void create_学生无权限() {
        var token = loginAs("student1");
        var body = Map.<String, Object>of(
                "activityName", "学生创建活动",
                "typeId", 1,
                "orgId", 1,
                "startTime", "2026-08-20 14:00:00",
                "endTime", "2026-08-20 17:00:00",
                "registrationDeadline", "2026-08-19 18:00:00",
                "location", "测试地点",
                "capacity", 30
        );
        var resp = post("/api/activities", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("只有管理员或组织负责人可以发布活动");
    }

    // ======================== 活动状态管理 ========================

    /** DRAFT → OPEN → CLOSED → FINISHED → OFFLINE 每一步都应成功 */
    @Test
    void status_有效状态链() {
        var token = loginAs("leader1");
        long activityId = 3;

        var transitions = List.of("OPEN", "CLOSED", "FINISHED", "OFFLINE");
        for (var target : transitions) {
            var resp = patch("/api/activities/" + activityId + "/status",
                    Map.of("status", target), token);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().code()).isEqualTo(0);

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) resp.getBody().data();
            assertThat(data).containsEntry("status", target);
        }
    }

    /** 从 DRAFT 直接跳转到 FINISHED 应被拒绝 */
    @Test
    void status_无效跳转() {
        var token = loginAs("leader1");
        // 活动3 当前为 DRAFT，尝试跳转到 FINISHED
        var resp = patch("/api/activities/3/status",
                Map.of("status", "FINISHED"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("不允许");
    }

    // ======================== 报名 ========================

    /** 用户(admin有STUDENT角色)可以报名开放中的活动 */
    @Test
    void register_正常报名() {
        var token = loginAs("admin");
        // 活动1 是 OPEN 状态，有剩余名额，admin尚未报名
        var resp = post("/api/activities/1/register", Map.of(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /**
     * 活动已满员时继续报名应被拒绝。
     *
     * 做这个测试前先插入一个容量=1且已满员的活动，
     * 保证截止时间在未来，让容量检查先于时间检查触发。
     */
    @Test
    @Sql(statements = {
            "insert into activity(activity_id, activity_name, type_id, org_id, start_time, end_time, location,"
                    + " registration_deadline, capacity, registered_count, description, requirement, rule_id, activity_status, created_by)"
                    + " values(100, '满员测试活动', 1, 1, '2026-09-20 14:00:00', '2026-09-20 17:00:00', '测试地',"
                    + " '2026-09-19 18:00:00', 1, 1, '满员测试', '满员测试', 1, 'OPEN', 4)",
            "insert into registration(registration_id, activity_id, user_id, registration_status, checkin_status)"
                    + " values(100, 100, 2, 'VALID', 'NOT_CHECKED')"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void register_活动已满() {
        var token = loginAs("student2");
        var resp = post("/api/activities/100/register", Map.of(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("名额已满");
    }

    /** 报名截止时间后报名应被拒绝 */
    @Test
    void register_报名截止已过() {
        var token = loginAs("student2");
        // 活动5 的截止日期是 2025-12-31（已过去），deadline 检查在容量检查之前
        var resp = post("/api/activities/5/register", Map.of(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("报名时间已截止");
    }

    // ======================== 取消报名 ========================

    /** 已报名的学生可以取消报名 */
    @Test
    void register_取消报名() {
        var token = loginAs("student1");
        // student1 已报名活动1
        var resp = rest.exchange("/api/activities/1/register", HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)), ApiResponse.class);
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
}
