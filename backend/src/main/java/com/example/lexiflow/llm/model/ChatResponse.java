package com.example.lexiflow.llm.model;

public record ChatResponse(
        String content,
        TokenUsage usage,
        String provider,
        String model
) {
}

