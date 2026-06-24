package com.example.campus.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.campus.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
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
                5,
                800
        ));

        AiClient.AiResult result = client.chat("系统提示", "用户问题");

        assertThat(requestBody.get()).contains("\"model\":\"test-model\"");
        assertThat(requestBody.get()).contains("\"max_tokens\":800");
        assertThat(requestBody.get()).contains("\"role\":\"system\"");
        assertThat(requestBody.get()).contains("\"role\":\"user\"");
        assertThat(authHeader.get()).isEqualTo("Bearer test-key");
        assertThat(result.answer()).isEqualTo("可以报名志愿服务活动。");
        assertThat(result.modelName()).isEqualTo("test-model");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.completionTokens()).isEqualTo(8);
    }

    @Test
    void rejectsDisabledServiceBeforeCallingProvider() {
        AiClient client = new AiClient(new AiProperties(false, "http://127.0.0.1:1/v1", "key", "model", 5, 800));

        assertThatThrownBy(() -> client.chat("系统提示", "用户问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 服务未启用，请配置 AI_ENABLED=true");
    }

    @Test
    void rejectsMissingApiKeyBeforeCallingProvider() {
        AiClient client = new AiClient(new AiProperties(true, "http://127.0.0.1:1/v1", "", "model", 5, 800));

        assertThatThrownBy(() -> client.chat("系统提示", "用户问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI API Key 未配置");
    }
}
