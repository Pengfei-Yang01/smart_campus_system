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
 * - 为已结束活动录入积分成功
 * - 为未结束活动录入积分被拒绝
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ScoreControllerTest {

    @Autowired
    private TestRestTemplate rest;

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
}
