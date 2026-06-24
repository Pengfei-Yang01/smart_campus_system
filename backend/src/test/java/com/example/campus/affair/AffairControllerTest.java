package com.example.campus.affair;

import com.example.campus.common.ApiResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 学生事务申请模块测试。
 *
 * 覆盖普通学生申请、组织负责人专属资源权限、管理员审批和资源冲突校验。
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AffairControllerTest {
    @Autowired
    private TestRestTemplate rest;

    /** 普通学生可以提交桌椅借用申请。 */
    @Test
    void create_学生提交桌椅申请成功() {
        var token = loginAs("student1");
        var resp = post("/api/affairs/applications", Map.of(
                "typeId", 1,
                "title", "测试桌椅申请",
                "applyReason", "班级活动需要桌椅",
                "expectedStart", "2026-07-10 09:00:00",
                "expectedEnd", "2026-07-10 12:00:00",
                "quantity", 5,
                "contact", "student1@campus.local"
        ), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 普通学生不能申请组织负责人专属的教室资源。 */
    @Test
    void create_学生申请教室被拒绝() {
        var token = loginAs("student1");
        var resp = post("/api/affairs/applications", Map.of(
                "typeId", 3,
                "resourceId", 2,
                "title", "学生教室申请",
                "applyReason", "尝试申请教室",
                "expectedStart", "2026-07-11 09:00:00",
                "expectedEnd", "2026-07-11 12:00:00",
                "quantity", 1
        ), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("只有组织负责人");
    }

    /** 组织负责人可以为自己负责的启用组织申请教室。 */
    @Test
    void create_负责人申请本组织教室成功() {
        var token = loginAs("leader1");
        var resp = post("/api/affairs/applications", Map.of(
                "typeId", 3,
                "resourceId", 3,
                "orgId", 1,
                "title", "负责人教室申请",
                "applyReason", "组织培训需要教室",
                "expectedStart", "2026-07-12 14:00:00",
                "expectedEnd", "2026-07-12 17:00:00",
                "quantity", 1
        ), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 管理员审批通过普通事务申请后，应返回通过状态。 */
    @Test
    void audit_管理员通过申请成功() {
        var token = loginAs("admin");
        var resp = patch("/api/admin/affairs/applications/1/audit", Map.of(
                "status", "APPROVED",
                "reviewRemark", "同意借用，请按时归还"
        ), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsEntry("status", "APPROVED");
    }

    /** 同一资源审批通过时必须校验时间重叠，防止重复占用教室。 */
    @Test
    @Sql(statements = {
            "insert into affair_application(affair_id, applicant_id, applicant_role, org_id, type_id, resource_id, title, apply_reason,"
                    + " expected_start, expected_end, quantity, contact, status)"
                    + " values(100, 4, 'ORG_LEADER', 1, 3, 2, '冲突教室申请', '测试资源冲突',"
                    + " '2026-07-06 15:00:00', '2026-07-06 16:00:00', 1, 'leader1@campus.local', 'PENDING')"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void audit_资源时间冲突被拒绝() {
        var token = loginAs("admin");
        var resp = patch("/api/admin/affairs/applications/100/audit", Map.of(
                "status", "APPROVED",
                "reviewRemark", "尝试通过冲突申请"
        ), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("已被占用");
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

    private ResponseEntity<ApiResponse> post(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, bearerHeaders(token)), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> patch(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, bearerHeaders(token)), ApiResponse.class);
    }
}
