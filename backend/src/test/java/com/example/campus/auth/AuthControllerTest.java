package com.example.campus.auth;

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
 * 认证模块约束测试。
 *
 * 覆盖场景:
 * - 正常登录成功返回令牌
 * - 密码错误被拒绝
 * - 禁用账号被拒绝
 * - 新用户注册成功并赋角色
 * - 携带有效令牌可获取当前用户
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AuthControllerTest {

    @Autowired
    private TestRestTemplate rest;

    /** 登录成功：返回 token 和用户信息 */
    @Test
    void login_成功() {
        var body = Map.of("username", "admin", "password", "123456");
        var resp = rest.postForEntity("/api/auth/login", body, ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsKeys("token", "user");
        assertThat(data.get("token")).isNotNull();
    }

    /** 密码错误：返回 400 和错误消息 */
    @Test
    void login_密码错误() {
        var body = Map.of("username", "admin", "password", "wrong-password");
        var resp = rest.postForEntity("/api/auth/login", body, ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("账号或密码错误");
    }

    /** 禁用账号登录：返回 400 */
    @Test
    void login_账号已禁用() {
        var body = Map.of("username", "disabled_stu", "password", "123456");
        var resp = rest.postForEntity("/api/auth/login", body, ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("账号已被禁用");
    }

    /** 注册新学生：返回 token */
    @Test
    void register_成功注册() {
        var body = Map.<String, Object>of(
                "username", "newstudent",
                "password", "pass123",
                "studentNo", "S2023099",
                "realName", "新学生",
                "college", "测试学院",
                "major", "测试专业",
                "className", "测试班",
                "grade", "2024"
        );
        var resp = rest.postForEntity("/api/auth/register", body, ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        assertThat(data).containsKey("token");
        assertThat(data).containsKey("user");
    }

    /** 使用有效令牌访问 /api/auth/me 应返回当前用户信息 */
    @Test
    void me_获取当前用户() {
        // 先登录获取令牌
        String token = loginAs("student1");

        // 使用令牌请求 /me
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var meResp = rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), ApiResponse.class);
        assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResp.getBody()).isNotNull();
        assertThat(meResp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> me = (Map<String, Object>) meResp.getBody().data();
        assertThat(me).containsEntry("username", "student1");
    }

    // ---- 辅助方法 ----

    /** 登录指定用户并返回令牌字符串 */
    @SuppressWarnings("unchecked")
    private String loginAs(String username) {
        var body = Map.of("username", username, "password", "123456");
        var resp = rest.postForEntity("/api/auth/login", body, ApiResponse.class);
        var data = (Map<String, Object>) resp.getBody().data();
        return (String) data.get("token");
    }
}
