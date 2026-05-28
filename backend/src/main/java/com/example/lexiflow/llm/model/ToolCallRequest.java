package com.example.lexiflow.llm.model;

import java.util.List;
import java.util.Map;

public record ToolCallRequest(
        ChatRequest chatRequest,
        List<ToolDefinition> tools
) {

    public record ToolDefinition(
            String name,
            String description,
            Map<String, Object> parametersSchema
    ) {
    }
}

