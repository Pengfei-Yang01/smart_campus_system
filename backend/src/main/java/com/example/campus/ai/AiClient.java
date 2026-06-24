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

/**
 * Minimal OpenAI-compatible chat completions client.
 */
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
                    "max_tokens", properties.maxOutputTokensOrDefault(),
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
