package com.example.campus.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.AiRequests.ChatRequest;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiControllerTest {
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatBuildsContextCallsModelAndStoresRecord() {
        Db db = mock(Db.class);
        AiClient client = mock(AiClient.class);
        AiContextService contextService = mock(AiContextService.class);
        CurrentUser user = new CurrentUser(1L, "admin", "系统管理员", Set.of("ADMIN"));
        UserContext.set(user);
        when(contextService.build(user)).thenReturn("管理员平台摘要");
        when(client.chat(any(), any())).thenReturn(new AiClient.AiResult("可以报名志愿服务活动。", "test-model", 12, 8));

        AiController controller = new AiController(db, client, contextService);

        Map<String, Object> data = (Map<String, Object>) controller.chat(new ChatRequest("  最近有哪些活动？  ")).data();

        assertThat(data).containsEntry("answer", "可以报名志愿服务活动。");
        assertThat(data).containsEntry("modelName", "test-model");
        verify(client).chat(any(), org.mockito.ArgumentMatchers.contains("管理员平台摘要"));
        verify(db).insert(
                org.mockito.ArgumentMatchers.contains("insert into ai_qa_record"),
                eq(1L),
                eq("最近有哪些活动？"),
                eq("可以报名志愿服务活动。"),
                eq("test-model"),
                eq(12),
                eq(8),
                any()
        );
    }

    @Test
    void chatRejectsOverlongQuestionBeforeCallingModel() {
        Db db = mock(Db.class);
        AiClient client = mock(AiClient.class);
        AiContextService contextService = mock(AiContextService.class);
        UserContext.set(new CurrentUser(1L, "admin", "系统管理员", Set.of("ADMIN")));

        AiController controller = new AiController(db, client, contextService);

        assertThatThrownBy(() -> controller.chat(new ChatRequest("问".repeat(1001))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("问题不能超过 1000 字");
        verify(client, never()).chat(any(), any());
        verify(db, never()).insert(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatTruncatesOverlongAnswerBeforeStoring() {
        Db db = mock(Db.class);
        AiClient client = mock(AiClient.class);
        AiContextService contextService = mock(AiContextService.class);
        CurrentUser user = new CurrentUser(1L, "admin", "系统管理员", Set.of("ADMIN"));
        UserContext.set(user);
        when(contextService.build(user)).thenReturn("管理员平台摘要");
        when(client.chat(any(), any())).thenReturn(new AiClient.AiResult("答".repeat(20001), "test-model", 12, 8));

        AiController controller = new AiController(db, client, contextService);

        Map<String, Object> data = (Map<String, Object>) controller.chat(new ChatRequest("最近有哪些活动？")).data();

        String answer = String.valueOf(data.get("answer"));
        assertThat(answer).endsWith("（回答过长，已截断）");
        assertThat(answer.length()).isLessThan(20100);
        verify(db).insert(
                org.mockito.ArgumentMatchers.contains("insert into ai_qa_record"),
                eq(1L),
                eq("最近有哪些活动？"),
                eq(answer),
                eq("test-model"),
                eq(12),
                eq(8),
                any()
        );
    }
}
