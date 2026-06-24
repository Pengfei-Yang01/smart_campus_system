package com.example.campus.dto;

/**
 * Request bodies for AI assistant endpoints.
 */
public final class AiRequests {
    private AiRequests() {
    }

    public record ChatRequest(String question) {
    }
}
