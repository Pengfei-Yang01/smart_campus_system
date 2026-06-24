# AI Assistant LLM Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a configurable OpenAI-compatible AI assistant that answers with lightweight campus business context and stores per-user Q&A history.

**Architecture:** Add focused backend AI components under `com.example.campus.ai`: configuration, model client, context builder, and controller. Reuse the existing `Db`, `UserContext`, `ApiResponse`, and `ai_qa_record` table. Replace the placeholder Vue page with a non-streaming chat UI that calls `/api/ai/chat` and `/api/ai/records`.

**Tech Stack:** Spring Boot 3.3.6, Java `HttpClient`, JDBC via existing `Db`, Vue 3, Element Plus, axios wrapper in `front/src/api/http.js`.

---

## File Structure

- Create: `backend/src/main/java/com/example/campus/ai/AiProperties.java`
  - Binds `app.ai.*` settings from `application.yml`.
- Create: `backend/src/main/java/com/example/campus/ai/AiClient.java`
  - Calls OpenAI-compatible `/chat/completions` and parses answer plus token usage.
- Create: `backend/src/main/java/com/example/campus/ai/AiContextService.java`
  - Builds a short business context string from current user and permitted data.
- Create: `backend/src/main/java/com/example/campus/ai/AiController.java`
  - Validates requests, calls context service and AI client, writes `ai_qa_record`, returns records.
- Create: `backend/src/main/java/com/example/campus/dto/AiRequests.java`
  - Defines `ChatRequest`.
- Modify: `backend/src/main/java/com/example/campus/SmartCampusApplication.java`
  - Enables configuration properties scanning or explicitly enables `AiProperties`.
- Modify: `backend/src/main/resources/application.yml`
  - Adds `app.ai` environment-backed config.
- Create: `backend/src/test/java/com/example/campus/ai/AiClientTest.java`
  - Uses a local test HTTP server to verify OpenAI-compatible request/response parsing.
- Create: `backend/src/test/java/com/example/campus/ai/AiControllerTest.java`
  - Uses `@SpringBootTest` + `@AutoConfigureMockMvc` for validation and disabled-service behavior.
- Modify: `front/src/views/AiAssistant.vue`
  - Replaces placeholder with chat UI and history loading.

---

### Task 1: Backend AI Configuration

**Files:**
- Create: `backend/src/main/java/com/example/campus/ai/AiProperties.java`
- Modify: `backend/src/main/java/com/example/campus/SmartCampusApplication.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Write configuration binding class**

Create `backend/src/main/java/com/example/campus/ai/AiProperties.java`:

```java
package com.example.campus.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String apiBaseUrl,
        String apiKey,
        String model,
        int timeoutSeconds
) {
    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds <= 0 ? 30 : timeoutSeconds);
    }

    public String normalizedBaseUrl() {
        String value = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.openai.com/v1"
                : apiBaseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public String modelOrDefault() {
        return model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();
    }
}
```

- [ ] **Step 2: Enable configuration properties**

Modify `backend/src/main/java/com/example/campus/SmartCampusApplication.java` to include:

```java
package com.example.campus;

import com.example.campus.ai.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class SmartCampusApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartCampusApplication.class, args);
    }
}
```

- [ ] **Step 3: Add environment-backed config**

Modify `backend/src/main/resources/application.yml` under existing `app:`:

```yaml
app:
  token-secret: ${TOKEN_SECRET:smart-campus-dev-secret}
  token-hours: 12
  ai:
    enabled: ${AI_ENABLED:false}
    api-base-url: ${AI_API_BASE_URL:https://api.openai.com/v1}
    api-key: ${AI_API_KEY:}
    model: ${AI_MODEL:gpt-4o-mini}
    timeout-seconds: ${AI_TIMEOUT_SECONDS:30}
```

- [ ] **Step 4: Run build**

Run:

```powershell
$env:JAVA_HOME='D:\IntelliJ IDEA 2024.2.0.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\IntelliJ IDEA 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/campus/ai/AiProperties.java backend/src/main/java/com/example/campus/SmartCampusApplication.java backend/src/main/resources/application.yml
git commit -m "feat: add ai configuration"
```

---

### Task 2: OpenAI-Compatible Client

**Files:**
- Create: `backend/src/main/java/com/example/campus/ai/AiClient.java`
- Test: `backend/src/test/java/com/example/campus/ai/AiClientTest.java`

- [ ] **Step 1: Write failing client test**

Create `backend/src/test/java/com/example/campus/ai/AiClientTest.java`:

```java
package com.example.campus.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOpenAiCompatibleRequestAndParsesResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "choices": [{"message": {"content": "可以报名志愿服务活动。"}}],
                      "usage": {"prompt_tokens": 12, "completion_tokens": 8}
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AiClient client = new AiClient(new AiProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-key",
                "test-model",
                5
        ));

        AiClient.AiResult result = client.chat("系统提示", "用户问题");

        assertThat(requestBody.get()).contains("\"model\":\"test-model\"");
        assertThat(authHeader.get()).isEqualTo("Bearer test-key");
        assertThat(result.answer()).isEqualTo("可以报名志愿服务活动。");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.completionTokens()).isEqualTo(8);
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='D:\IntelliJ IDEA 2024.2.0.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\IntelliJ IDEA 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' -Dtest=AiClientTest test
```

Expected: compile failure because `AiClient` does not exist.

- [ ] **Step 3: Implement `AiClient`**

Create `backend/src/main/java/com/example/campus/ai/AiClient.java`:

```java
package com.example.campus.ai;

import com.example.campus.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiClient {
    private final AiProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiClient(AiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    }

    public AiResult chat(String systemPrompt, String userPrompt) {
        if (!properties.enabled()) {
            throw new BusinessException("AI 服务未启用，请配置 AI_ENABLED=true");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new BusinessException("AI API Key 未配置");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "model", properties.modelOrDefault(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.normalizedBaseUrl() + "/chat/completions"))
                    .timeout(properties.timeout())
                    .header("Authorization", "Bearer " + properties.apiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("AI 服务暂时不可用，请稍后重试");
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.at("/choices/0/message/content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BusinessException("AI 服务返回格式异常");
            }
            JsonNode usage = root.path("usage");
            return new AiResult(
                    content.asText(),
                    properties.modelOrDefault(),
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new BusinessException("AI 服务响应超时，请稍后重试");
        } catch (Exception ex) {
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    public record AiResult(String answer, String modelName, int promptTokens, int completionTokens) {
    }
}
```

- [ ] **Step 4: Run test and verify pass**

Run:

```powershell
$env:JAVA_HOME='D:\IntelliJ IDEA 2024.2.0.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\IntelliJ IDEA 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' -Dtest=AiClientTest test
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/campus/ai/AiClient.java backend/src/test/java/com/example/campus/ai/AiClientTest.java
git commit -m "feat: add openai compatible ai client"
```

---

### Task 3: AI Business Context Builder

**Files:**
- Create: `backend/src/main/java/com/example/campus/ai/AiContextService.java`

- [ ] **Step 1: Implement context service**

Create `backend/src/main/java/com/example/campus/ai/AiContextService.java`:

```java
package com.example.campus.ai;

import com.example.campus.common.Db;
import com.example.campus.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiContextService {
    private final Db db;

    public AiContextService(Db db) {
        this.db = db;
    }

    public String build(CurrentUser user) {
        StringBuilder text = new StringBuilder();
        text.append("当前用户：").append(user.realName()).append("（").append(user.username()).append("）\n");
        text.append("角色：").append(user.roles()).append("\n\n");

        appendRows(text, "最近开放活动", db.jdbc().queryForList("""
                select a.activity_name, t.type_name, o.org_name, a.start_time, a.location,
                       greatest(a.capacity - coalesce(count(r.registration_id),0), 0) remaining_count
                from activity a
                join activity_type t on a.type_id = t.type_id
                join organization o on a.org_id = o.org_id
                left join registration r on a.activity_id = r.activity_id and r.registration_status='VALID'
                where a.activity_status='OPEN'
                group by a.activity_id, a.activity_name, t.type_name, o.org_name, a.start_time, a.location, a.capacity
                order by a.start_time
                limit 5
                """));

        appendRows(text, "我的最近报名", db.jdbc().queryForList("""
                select a.activity_name, r.registration_status, r.checkin_status, a.activity_status
                from registration r
                join activity a on r.activity_id = a.activity_id
                where r.user_id = ?
                order by r.registered_at desc
                limit 5
                """, user.userId()));

        appendRows(text, "我的积分概览", db.jdbc().queryForList("""
                select coalesce(sum(case when audit_status='APPROVED' then final_score else 0 end),0) approved_score,
                       sum(case when audit_status='PENDING' then 1 else 0 end) pending_count
                from score_record
                where user_id = ?
                """, user.userId()));

        appendRows(text, "我的最近积分", db.jdbc().queryForList("""
                select a.activity_name, sr.final_score, sr.audit_status
                from score_record sr
                join activity a on sr.activity_id = a.activity_id
                where sr.user_id = ?
                order by sr.submitted_at desc
                limit 5
                """, user.userId()));

        if (user.isLeader()) {
            appendRows(text, "我负责的组织", db.jdbc().queryForList("""
                    select org_id, org_name, org_status
                    from organization
                    where principal_user_id = ?
                    limit 5
                    """, user.userId()));
            appendRows(text, "负责人待办", db.jdbc().queryForList("""
                    select count(*) pending_join_count
                    from organization_member om
                    join organization o on om.org_id = o.org_id
                    where o.principal_user_id = ? and om.join_status='PENDING'
                    """, user.userId()));
        }

        if (user.isAdmin()) {
            appendRows(text, "管理员平台摘要", db.jdbc().queryForList("""
                    select
                      (select count(*) from user_account) user_count,
                      (select count(*) from activity) activity_count,
                      (select count(*) from organization) organization_count,
                      (select count(*) from leader_apply where status='PENDING') pending_leader_count,
                      (select count(*) from organization_apply where status='PENDING') pending_org_count,
                      (select count(*) from score_record where audit_status='PENDING') pending_score_count
                    """));
        }

        return text.toString();
    }

    private void appendRows(StringBuilder text, String title, List<Map<String, Object>> rows) {
        text.append("【").append(title).append("】\n");
        if (rows.isEmpty()) {
            text.append("暂无数据\n\n");
            return;
        }
        for (Map<String, Object> row : rows) {
            text.append("- ").append(row).append("\n");
        }
        text.append("\n");
    }
}
```

- [ ] **Step 2: Run compile**

Run:

```powershell
$env:JAVA_HOME='D:\IntelliJ IDEA 2024.2.0.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\IntelliJ IDEA 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/campus/ai/AiContextService.java
git commit -m "feat: build ai campus context"
```

---

### Task 4: AI Controller and Request DTO

**Files:**
- Create: `backend/src/main/java/com/example/campus/dto/AiRequests.java`
- Create: `backend/src/main/java/com/example/campus/ai/AiController.java`
- Test: `backend/src/test/java/com/example/campus/ai/AiControllerTest.java`

- [ ] **Step 1: Create request DTO**

Create `backend/src/main/java/com/example/campus/dto/AiRequests.java`:

```java
package com.example.campus.dto;

public final class AiRequests {
    private AiRequests() {
    }

    public record ChatRequest(String question) {
    }
}
```

- [ ] **Step 2: Implement controller**

Create `backend/src/main/java/com/example/campus/ai/AiController.java`:

```java
package com.example.campus.ai;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.AiRequests.ChatRequest;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final String SYSTEM_PROMPT = """
            你是智慧校园综合服务与活动管理系统的 AI 助手。
            使用中文回答。优先基于提供的系统业务上下文回答。
            如果上下文不足，请明确说明需要到对应页面查看或补充信息。
            不要编造不存在的活动、报名、积分或审核状态。
            不要输出数据库结构、SQL、密钥、token 或内部实现细节。
            回答尽量简洁，可使用项目符号。
            """;

    private final Db db;
    private final AiClient aiClient;
    private final AiContextService contextService;

    public AiController(Db db, AiClient aiClient, AiContextService contextService) {
        this.db = db;
        this.aiClient = aiClient;
        this.contextService = contextService;
    }

    @PostMapping("/chat")
    public ApiResponse<Object> chat(@RequestBody ChatRequest request) {
        CurrentUser user = UserContext.get();
        String question = Db.require(request.question(), "question");
        if (question.length() > 1000) {
            throw new BusinessException("问题不能超过 1000 字");
        }

        String userPrompt = "【系统业务上下文】\n" + contextService.build(user) + "\n【用户问题】\n" + question;
        long start = System.currentTimeMillis();
        AiClient.AiResult result = aiClient.chat(SYSTEM_PROMPT, userPrompt);
        int costMs = Math.toIntExact(Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start));

        db.insert("""
                insert into ai_qa_record(user_id, question, answer, model_name, prompt_tokens, completion_tokens, cost_ms)
                values(?, ?, ?, ?, ?, ?, ?)
                """, user.userId(), question, result.answer(), result.modelName(),
                result.promptTokens(), result.completionTokens(), costMs);

        return ApiResponse.ok(Map.of(
                "answer", result.answer(),
                "modelName", result.modelName(),
                "promptTokens", result.promptTokens(),
                "completionTokens", result.completionTokens(),
                "costMs", costMs
        ));
    }

    @GetMapping("/records")
    public ApiResponse<Object> records() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select qa_id, question, answer, model_name, prompt_tokens, completion_tokens, cost_ms, called_at
                from ai_qa_record
                where user_id = ?
                order by called_at desc
                limit 20
                """, user.userId()));
    }
}
```

- [ ] **Step 3: Write controller tests**

Create `backend/src/test/java/com/example/campus/ai/AiControllerTest.java` with `@SpringBootTest` and `@AutoConfigureMockMvc`. Run this test against the same local MySQL test database used for development, with `DB_URL` pointing at `127.0.0.1:3307`.

Minimum test code:

```java
package com.example.campus.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.ai.enabled=false",
        "app.ai.api-key="
})
class AiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMissingTokenBeforeAiLogic() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }
}
```

Keep this test focused on proving `/api/ai/chat` remains protected by the existing auth interceptor. Do not disable authentication for the controller.

- [ ] **Step 4: Run tests**

Run:

```powershell
$env:JAVA_HOME='D:\IntelliJ IDEA 2024.2.0.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\IntelliJ IDEA 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' test
```

Expected: all tests pass. Before running, set `DB_URL` to the existing local development database:

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3307/smart_campus?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
$env:DB_USERNAME='root'
$env:DB_PASSWORD=''
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/campus/dto/AiRequests.java backend/src/main/java/com/example/campus/ai/AiController.java backend/src/test/java/com/example/campus/ai/AiControllerTest.java
git commit -m "feat: add ai chat api"
```

---

### Task 5: Frontend AI Chat Page

**Files:**
- Modify: `front/src/views/AiAssistant.vue`

- [ ] **Step 1: Replace placeholder UI**

Modify `front/src/views/AiAssistant.vue` to use the existing `AppLayout`, `http`, and Element Plus controls:

```vue
<template>
  <AppLayout title="AI 助手" subtitle="结合活动、报名、组织和积分数据回答校园事务问题">
    <section class="panel ai-page">
      <div class="ai-history">
        <div v-if="messages.length === 0" class="ai-empty">
          <el-empty description="还没有问答记录，可以先问问最近有哪些活动适合报名。" />
        </div>
        <div v-for="message in messages" :key="message.id" class="ai-message" :class="message.role">
          <div class="ai-message-role">{{ message.role === 'user' ? '我' : 'AI 助手' }}</div>
          <div class="ai-message-content">{{ message.content }}</div>
          <div v-if="message.meta" class="ai-message-meta">{{ message.meta }}</div>
        </div>
      </div>

      <div class="ai-input-row">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="例如：最近有哪些活动适合我报名？我的积分情况怎么样？"
          @keydown.ctrl.enter.prevent="send"
        />
        <el-button type="primary" :loading="sending" :disabled="!question.trim()" @click="send">发送</el-button>
      </div>
    </section>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const question = ref('')
const sending = ref(false)
const messages = ref([])

onMounted(loadRecords)

async function loadRecords() {
  const records = await http.get('/ai/records')
  messages.value = records.flatMap((record) => [
    {
      id: `q-${record.qa_id}`,
      role: 'user',
      content: record.question
    },
    {
      id: `a-${record.qa_id}`,
      role: 'assistant',
      content: record.answer,
      meta: `${record.model_name} · ${record.cost_ms || 0}ms`
    }
  ]).reverse()
}

async function send() {
  const text = question.value.trim()
  if (!text || sending.value) return
  const userMessage = { id: `local-q-${Date.now()}`, role: 'user', content: text }
  messages.value.push(userMessage)
  question.value = ''
  sending.value = true
  try {
    const result = await http.post('/ai/chat', { question: text })
    messages.value.push({
      id: `local-a-${Date.now()}`,
      role: 'assistant',
      content: result.answer,
      meta: `${result.modelName} · ${result.costMs || 0}ms`
    })
  } finally {
    sending.value = false
  }
}
</script>
```

- [ ] **Step 2: Add scoped or page-level styles**

Add styles inside `AiAssistant.vue`:

```vue
<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 620px;
}

.ai-history {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding-right: 4px;
}

.ai-empty {
  margin: auto;
}

.ai-message {
  max-width: 78%;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  white-space: pre-wrap;
  line-height: 1.6;
}

.ai-message.user {
  align-self: flex-end;
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.ai-message.assistant {
  align-self: flex-start;
}

.ai-message-role {
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}

.ai-message-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.ai-input-row {
  display: grid;
  grid-template-columns: 1fr 96px;
  gap: 12px;
  align-items: stretch;
}

@media (max-width: 720px) {
  .ai-message {
    max-width: 100%;
  }

  .ai-input-row {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm run build
```

Expected: Vite build succeeds.

- [ ] **Step 4: Commit**

```bash
git add front/src/views/AiAssistant.vue
git commit -m "feat: add ai assistant chat page"
```

---

### Task 6: End-to-End Verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Run backend tests**

Run:

```powershell
$env:JAVA_HOME='D:\IntelliJ IDEA 2024.2.0.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\IntelliJ IDEA 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run backend package**

Run:

```powershell
$env:JAVA_HOME='D:\IntelliJ IDEA 2024.2.0.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\IntelliJ IDEA 2024.2.0.2\plugins\maven\lib\maven3\bin\mvn.cmd' -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm run build
```

Expected: `✓ built`.

- [ ] **Step 4: Verify disabled AI behavior**

Start backend with:

```powershell
$env:AI_ENABLED='false'
$env:DB_URL='jdbc:mysql://127.0.0.1:3307/smart_campus?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
$env:DB_USERNAME='root'
$env:DB_PASSWORD=''
java -jar backend/target/smart-campus-system-1.0.0.jar
```

Login through the frontend as `admin / 123456`, open `/ai`, send a question.

Expected: frontend shows “AI 服务未启用，请配置 AI_ENABLED=true”.

- [ ] **Step 5: Verify real provider behavior**

Start backend with:

```powershell
$env:AI_ENABLED='true'
$env:AI_API_BASE_URL='https://api.openai.com/v1'
$env:AI_API_KEY='paste-your-local-api-key-here'
$env:AI_MODEL='gpt-4o-mini'
$env:DB_URL='jdbc:mysql://127.0.0.1:3307/smart_campus?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
$env:DB_USERNAME='root'
$env:DB_PASSWORD=''
java -jar backend/target/smart-campus-system-1.0.0.jar
```

Login through the frontend as `admin / 123456`, open `/ai`, ask:

```text
最近有哪些活动可以报名？请结合我的身份回答。
```

Expected:

- Response is in Chinese.
- Response refers to available activities or states that data is insufficient.
- `GET /api/ai/records` returns the new record.
- Database query confirms insert:

```powershell
& 'D:\lenovo\mysql-8.0.31-winx64\mysql-8.0.31-winx64\bin\mysql.exe' --protocol=TCP --host=127.0.0.1 --port=3307 --default-character-set=utf8mb4 -uroot --execute="use smart_campus; select qa_id, user_id, model_name, cost_ms from ai_qa_record order by qa_id desc limit 3;"
```

- [ ] **Step 6: Commit verification fixes**

If verification required small fixes, commit the exact files changed during verification. Example for a frontend-only polish fix:

```bash
git status --short
git add front/src/views/AiAssistant.vue
git commit -m "fix: polish ai assistant integration"
```

---

## Notes for Execution

- Keep API keys out of source files and commits.
- Do not expose provider errors verbatim to the frontend because they may include request identifiers or account details.
- Do not add streaming in this pass.
- Do not add new database tables in this pass.
- Preserve the existing `.gitignore` change separately; it came from local runtime setup and is not part of the AI feature.
