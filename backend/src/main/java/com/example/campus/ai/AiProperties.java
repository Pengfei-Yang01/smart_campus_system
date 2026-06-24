package com.example.campus.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI provider configuration loaded from environment-backed application.yml values.
 */
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
