package com.example.lexiflow.llm.model;

import java.util.Map;

public record StructuredOutputResponse(Map<String, Object> data, TokenUsage usage, String provider, String model) {
}

