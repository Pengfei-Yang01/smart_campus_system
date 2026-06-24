package com.example.campus.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campus.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 学生端接口测试。
 *
 * 覆盖场景：
 * - 学生首页聚合数据
 * - 我的报名记录
 * - 我的积分记录
 * - 负责人申请成功
 * - 已有待审核申请时拒绝重复提交
 * - 管理员或组织负责人不需要重复申请负责人角色
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StudentControllerTest {

    @Autowired
    private TestRestTemplate rest;

    /** 学生首页应返回近期活动、报名数量、待审核积分和负责人申请信息。 */
    @Test
    void dashboard_学生首页聚合数据() {
        var token = loginAs("student1");
        var resp = get("/api/dashboard/student", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsKeys("recentActivities", "registrationCount",
                "pendingScoreCount", "approvedScore", "leaderApply");
        assertThat(((Number) data.get("registrationCount")).intValue()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        var leaderApply = (Map<String, Object>) data.get("leaderApply");
        assertThat(leaderApply).containsEntry("status", "PENDING");
    }

    /** 我的报名接口应只返回当前登录学生自己的报名记录。 */
    @Test
    void myRegistrations_查询当前学生报名记录() {
        var token = loginAs("student1");
        var resp = get("/api/me/registrations", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) resp.getBody().data();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsKeys("activity_name", "activity_status", "org_name", "type_name");
    }

    /** 我的积分接口应只返回当前登录学生自己的积分记录。 */
    @Test
    void myScores_查询当前学生积分记录() {
        var token = loginAs("student2");
        var resp = get("/api/me/scores", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) resp.getBody().data();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsKeys("activity_name", "org_name", "type_name", "final_score");
    }

    /** 普通学生没有待审核负责人申请时，可以提交负责人申请。 */
    @Test
    void applyLeader_普通学生提交申请成功() {
        var token = loginAs("student2");
        var body = Map.of(
                "applyReason", "希望负责组织活动测试",
                "contact", "student2@campus.local",
                "experience", "参与过多次校园活动"
        );
        var resp = post("/api/students/leader-apply", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsKey("applyId");
    }

    /** 已有待审核负责人申请时，不能重复提交。 */
    @Test
    void applyLeader_已有待审核申请时失败() {
        var token = loginAs("student1");
        var resp = post("/api/students/leader-apply",
                Map.of("applyReason", "重复申请测试"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isNotEqualTo(0);
    }

    /** 组织负责人已经拥有负责人权限，不应再次提交负责人申请。 */
    @Test
    void applyLeader_组织负责人重复申请失败() {
        var token = loginAs("leader1");
        var resp = post("/api/students/leader-apply",
                Map.of("applyReason", "负责人重复申请测试"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isNotEqualTo(0);
    }

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
}
