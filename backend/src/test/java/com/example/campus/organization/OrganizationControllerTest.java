package com.example.campus.organization;

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
 * 组织模块约束测试。
 *
 * 覆盖场景:
 * - 组织负责人申请创建组织
 * - 普通学生申请创建组织被拒绝
 * - 学生加入活跃组织
 * - 停用组织拒绝加入
 * - 已加入或待审核的成员拒绝重复申请
 * - 负责人审核通过成员加入
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrganizationControllerTest {

    @Autowired
    private TestRestTemplate rest;

    // ======================== 组织创建申请 ========================

    /** 组织负责人可以提交组织创建申请 */
    @Test
    void apply_负责人申请创建组织() {
        var token = loginAs("leader1");
        var body = Map.of(
                "orgName", "测试新组织",
                "orgType", "CLUB",
                "description", "测试用新组织",
                "applyReason", "需要更多活动空间",
                "contact", "test@campus.local"
        );
        var resp = post("/api/organizations/apply", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 普通学生不能提交组织创建申请（角色权限约束） */
    @Test
    void apply_学生无权限() {
        var token = loginAs("student1");
        var body = Map.of(
                "orgName", "学生想建组织",
                "orgType", "CLUB",
                "description", "测试",
                "applyReason", "试试看",
                "contact", "stu@campus.local"
        );
        var resp = post("/api/organizations/apply", body, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("只有组织负责人可以申请创建组织");
    }

    // ======================== 加入组织 ========================

    /** 学生可以申请加入活跃组织，申请状态应为 PENDING */
    @Test
    void join_加入活跃组织() {
        var token = loginAs("student1");
        // student1 尚未加入组织2（青年志愿者服务队，ACTIVE）
        var resp = post("/api/organizations/2/join", Map.of("applyReason", "想参加志愿活动"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 停用状态的组织不允许学生申请加入。 */
    @Test
    void join_停用组织失败() {
        var token = loginAs("student1");
        var resp = post("/api/organizations/3/join", Map.of("applyReason", "测试停用组织"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isNotEqualTo(0);
    }

    /** 已经加入组织的学生不能重复申请加入。 */
    @Test
    void join_已加入组织不能重复申请() {
        var token = loginAs("student1");
        var resp = post("/api/organizations/1/join", Map.of("applyReason", "重复申请"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isNotEqualTo(0);
    }

    /** 已有待审核申请时，不能重复提交加入申请。 */
    @Test
    void join_待审核申请不能重复提交() {
        var token = loginAs("student2");
        var resp = post("/api/organizations/1/join", Map.of("applyReason", "重复待审核申请"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isNotEqualTo(0);
    }

    // ======================== 成员审核 ========================

    /** 组织负责人可以通过待审核的成员申请 */
    @Test
    void auditMember_通过审核() {
        var token = loginAs("leader1");
        // 组织1 中 student2(userId=3) 的 join_status 为 PENDING
        var resp = patch("/api/organizations/1/members/3",
                Map.of("joinStatus", "APPROVED"), token);
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
