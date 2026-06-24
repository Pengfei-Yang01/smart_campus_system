package com.example.campus.message;

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
 * 消息通知中心测试。
 *
 * 覆盖收件箱读取、未读标记和管理员按角色发布公告。
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MessageControllerTest {
    @Autowired
    private TestRestTemplate rest;

    /** 用户可以查看未读数量，并把所有消息标记为已读。 */
    @Test
    void messages_读取并全部标为已读() {
        var token = loginAs("student1");
        var countResp = get("/api/messages/unread-count", token);
        assertThat(countResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var countData = (Map<String, Object>) countResp.getBody().data();
        assertThat(((Number) countData.get("count")).intValue()).isGreaterThan(0);

        var readResp = patch("/api/messages/read-all", Map.of(), token);
        assertThat(readResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var afterResp = get("/api/messages/unread-count", token);
        @SuppressWarnings("unchecked")
        var afterData = (Map<String, Object>) afterResp.getBody().data();
        assertThat(((Number) afterData.get("count")).intValue()).isEqualTo(0);
    }

    /** 管理员发布学生公告时，只应发给主角色为学生的用户。 */
    @Test
    void notice_按学生角色发布() {
        var adminToken = loginAs("admin");
        var publishResp = post("/api/admin/notices", Map.of(
                "title", "学生专属通知",
                "content", "这是一条只发给普通学生的测试通知。",
                "targetRole", "STUDENT",
                "priority", "IMPORTANT"
        ), adminToken);
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var studentMessages = messageRows(loginAs("student1"));
        assertThat(studentMessages).anyMatch(row -> "学生专属通知".equals(String.valueOf(row.get("title"))));

        var leaderMessages = messageRows(loginAs("leader1"));
        assertThat(leaderMessages).noneMatch(row -> "学生专属通知".equals(String.valueOf(row.get("title"))));
    }

    private List<Map<String, Object>> messageRows(String token) {
        var resp = get("/api/messages", token);
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) resp.getBody().data();
        return rows;
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
