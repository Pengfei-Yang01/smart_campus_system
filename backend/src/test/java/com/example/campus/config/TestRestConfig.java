package com.example.campus.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 测试配置：为 RestTemplate 启用 PATCH 方法支持。
 *
 * Spring Boot 的默认 SimpleClientHttpRequestFactory 底层使用
 * HttpURLConnection，它不支持 PATCH 方法。添加 httpclient5
 * 依赖并显示配置 HttpComponentsClientHttpRequestFactory
 * 可以解决这个问题。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestRestConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        var factory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        return builder.requestFactory(() -> factory).build();
    }
}
