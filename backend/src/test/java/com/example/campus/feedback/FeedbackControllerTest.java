package com.example.campus.feedback;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活动评价反馈模块测试。
 *
 * 覆盖评价资格、重复评价约束、负责人回复和评价隐藏逻辑。
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FeedbackControllerTest {
    @Autowired
    private TestRestTemplate rest;

    /** 已报名、已签到且活动已结束的学生可以提交评价。 */
    @Test
    @Sql(statements = {
            "insert into registration(registration_id, activity_id, user_id, registration_status, checkin_status)"
                    + " values(100, 2, 2, 'VALID', 'CHECKED')"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void create_已完成活动评价成功() {
        var token = loginAs("student1");
        var resp = post("/api/activities/2/feedbacks", Map.of(
                "rating", 5,
                "content", "活动安排清晰，收获很多。",
                "anonymous", false
        ), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 未结束或未签到的活动不能评价。 */
    @Test
    void create_未完成活动评价被拒绝() {
        var token = loginAs("student1");
        var resp = post("/api/activities/1/feedbacks", Map.of(
                "rating", 4,
                "content", "提前评价应被拒绝",
                "anonymous", false
        ), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("活动结束后");
    }

    /** 活动所属组织负责人可以回复评价。 */
    @Test
    void reply_所属组织负责人成功回复() {
        var token = loginAs("leader2");
        var resp = patch("/api/feedbacks/1/reply", Map.of("replyContent", "谢谢你的反馈，我们会继续优化。"), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(0);
    }

    /** 非所属组织负责人不能管理他人组织活动的评价。 */
    @Test
    void status_非所属负责人无权限() {
        var token = loginAs("leader1");
        var resp = patch("/api/feedbacks/1/status", Map.of("status", "HIDDEN"), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("没有权限");
    }

    /** 负责人隐藏评价后，活动详情公开评价列表不再展示该评价。 */
    @Test
    void status_隐藏后公开列表不可见() {
        var token = loginAs("leader2");
        var hideResp = patch("/api/feedbacks/1/status", Map.of("status", "HIDDEN"), token);
        assertThat(hideResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var publicResp = get("/api/activities/2/feedbacks", loginAs("student1"));
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) publicResp.getBody().data();
        assertThat(rows).noneMatch(row -> ((Number) row.get("feedback_id")).longValue() == 1L);
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
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> post(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, bearerHeaders(token)), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> patch(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, bearerHeaders(token)), ApiResponse.class);
    }
}
