package com.example.lexiflow.llm.model;

import java.util.Map;

public record StructuredOutputRequest(
        ChatRequest chatRequest,
        Map<String, Object> schema
) {
}

