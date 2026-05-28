package com.example.lexiflow.llm.model;

import java.util.List;
import java.util.Map;

public record ChatRequest(
        String scenario,
        String model,
        List<ChatMessage> messages,
        Map<String, Object> options
) {
}

