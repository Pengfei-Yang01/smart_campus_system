package com.example.campus.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务配置。
 *
 * 配置项来自 application.yml 中的 app.ai，并通过环境变量覆盖。
 * 默认关闭 AI，避免普通部署时因为缺少模型密钥影响系统启动。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String apiBaseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        int maxOutputTokens
) {
    /**
     * 获取请求超时时间，配置缺失或非法时使用 30 秒。
     */
    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds <= 0 ? 30 : timeoutSeconds);
    }

    /**
     * 规范化模型服务基础地址，去掉末尾多余斜杠。
     */
    public String normalizedBaseUrl() {
        String value = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.openai.com/v1"
                : apiBaseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * 获取模型名称，未配置时使用默认模型。
     */
    public String modelOrDefault() {
        return model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();
    }

    /**
     * 获取最大输出 token 数，未配置或非法时使用默认限制。
     */
    public int maxOutputTokensOrDefault() {
        return maxOutputTokens <= 0 ? 800 : maxOutputTokens;
    }
}
