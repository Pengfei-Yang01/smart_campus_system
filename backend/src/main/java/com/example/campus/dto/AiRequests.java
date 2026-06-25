package com.example.campus.dto;

/**
 * AI 助手模块的请求数据对象。
 */
public final class AiRequests {
    private AiRequests() {
    }

    /**
     * 用户向 AI 助手提交的问题。
     *
     * @param question 用户输入的自然语言问题
     */
    public record ChatRequest(String question) {
    }
}
