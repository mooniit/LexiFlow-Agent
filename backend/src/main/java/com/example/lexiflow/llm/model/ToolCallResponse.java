package com.example.lexiflow.llm.model;

import java.util.Map;

public record ToolCallResponse(
        String toolName,
        Map<String, Object> arguments,
        String assistantMessage,
        TokenUsage usage,
        String provider,
        String model
) {
}

