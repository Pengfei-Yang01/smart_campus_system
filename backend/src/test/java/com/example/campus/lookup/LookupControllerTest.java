package com.example.campus.lookup;

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
 * 公共字典和健康检查接口测试。
 *
 * 覆盖场景：
 * - 健康检查无需登录即可访问
 * - 活动类型字典返回初始化数据
 * - 启用中的积分规则字典返回初始化数据
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class LookupControllerTest {

    @Autowired
    private TestRestTemplate rest;

    /** 健康检查接口用于判断后端是否启动成功，不需要登录令牌。 */
    @Test
    void health_无需登录即可访问() {
        var resp = rest.getForEntity("/api/health", ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 活动类型字典应返回测试数据中的五类活动。 */
    @Test
    void activityTypes_查询活动类型字典() {
        var token = loginAs("student1");
        var resp = get("/api/activity-types", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) resp.getBody().data();
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0)).containsKeys("type_id", "type_code", "type_name");
    }

    /** 启用中的积分规则字典应返回每个活动类型对应的规则。 */
    @Test
    void scoreRules_查询启用积分规则字典() {
        var token = loginAs("student1");
        var resp = get("/api/score-rules", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) resp.getBody().data();
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0)).containsKeys("rule_id", "type_id", "type_name", "effective_status");
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
}
