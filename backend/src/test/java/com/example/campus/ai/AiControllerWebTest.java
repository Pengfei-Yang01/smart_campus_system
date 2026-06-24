package com.example.campus.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.common.GlobalExceptionHandler;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.AuthInterceptor;
import com.example.campus.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiControllerWebTest {
    private Db db;
    private JdbcTemplate jdbc;
    private AiClient aiClient;
    private AiContextService contextService;
    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        db = mock(Db.class);
        jdbc = mock(JdbcTemplate.class);
        aiClient = mock(AiClient.class);
        contextService = mock(AiContextService.class);
        tokenService = new TokenService(new ObjectMapper(), "test-secret", 12);
        when(db.jdbc()).thenReturn(jdbc);

        AiController controller = new AiController(db, aiClient, contextService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new AuthInterceptor(tokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void chatRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"你好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void chatReturnsUnifiedErrorWhenAiIsDisabled() throws Exception {
        when(contextService.build(any())).thenReturn("上下文");
        when(aiClient.chat(anyString(), anyString()))
                .thenThrow(new BusinessException("AI 服务未启用，请配置 AI_ENABLED=true"));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + tokenFor(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"你好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("AI 服务未启用，请配置 AI_ENABLED=true"));
    }

    @Test
    void recordsQueriesOnlyCurrentUser() throws Exception {
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of("qa_id", 1L)));

        mockMvc.perform(get("/api/ai/records")
                        .header("Authorization", "Bearer " + tokenFor(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].qa_id").value(1));

        verify(jdbc).queryForList(anyString(), eq(7L));
    }

    private String tokenFor(Long userId) {
        return tokenService.create(new CurrentUser(userId, "admin", "系统管理员", Set.of("ADMIN")));
    }
}
