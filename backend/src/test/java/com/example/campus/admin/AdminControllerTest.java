package com.example.campus.admin;

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
 * 管理员模块约束测试。
 *
 * 覆盖场景:
 * - 管理员统计接口返回正确计数
 * - 更新学生信息时手机号格式校验
 * - 组织停用级联作关活动和报名
 * - 审核通过负责人申请 → 自动授予 ORG_LEADER 角色
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AdminControllerTest {

    @Autowired
    private TestRestTemplate rest;

    // ======================== 统计 ========================

    /** 管理员的统计接口应返回所有关键计数 */
    @Test
    void stats_管理员统计() {
        var token = loginAs("admin");
        var resp = get("/api/admin/stats", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsKeys(
                "studentCount", "activityCount", "organizationCount",
                "pendingLeaderApplyCount", "pendingOrgApplyCount", "pendingScoreCount");
        // 验证基本数据：6个用户、5个活动、3个组织、1个待审负责人申请
        assertThat(data.get("studentCount")).isEqualTo(6);
        assertThat(data.get("activityCount")).isEqualTo(5);
        assertThat(data.get("organizationCount")).isEqualTo(3);
    }

    // ======================== 手机号格式校验 ========================

    /** 修改学生信息时，不合法的手机号应被拒绝 */
    @Test
    void updateStudent_手机号格式校验() {
        var token = loginAs("admin");
        // 手机号不足11位
        var body = Map.<String, Object>of(
                "realName", "测试姓名",
                "phone", "12345",
                "email", "test@test.com",
                "accountStatus", "ENABLED"
        );
        var resp = patch("/api/admin/students/2", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("电话号码必须为11位数字");
    }

    // ======================== 组织停用级联 ========================

    /** 停用组织后，该组织下的活动应变为 OFFLINE */
    @Test
    void disableOrg_级联停用活动() {
        var token = loginAs("admin");
        // 停用组织1（ACTIVE → DISABLED）
        var resp = patch("/api/admin/organizations/1/status",
                Map.of("orgStatus", "DISABLED"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        // 验证组织1下的活动1(原OPEN) 变为 OFFLINE
        var detailResp = get("/api/activities/1", token);
        @SuppressWarnings("unchecked")
        var detailData = (Map<String, Object>) detailResp.getBody().data();
        assertThat(detailData.get("activity_status")).isEqualTo("OFFLINE");
    }

    // ======================== 负责人审核 ========================

    /** 审核通过负责人申请后，用户应自动获得 ORG_LEADER 角色 */
    @Test
    void approveLeaderApply_角色授予() {
        var token = loginAs("admin");
        // student1 (userId=2) 有一个 PENDING 的负责人申请 (applyId=1)
        var resp = patch("/api/admin/leader-applies/1",
                Map.of("status", "APPROVED"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        // 验证 student1 的角色中包含了 ORG_LEADER
        var meResp = get("/api/auth/me", loginAs("student1"));
        @SuppressWarnings("unchecked")
        var meData = (Map<String, Object>) meResp.getBody().data();
        @SuppressWarnings("unchecked")
        var roles = (java.util.List<String>) meData.get("roles");
        assertThat(roles).contains("ORG_LEADER");
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

    private ResponseEntity<ApiResponse> patch(String path, Object body, String token) {
        var headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }
}
