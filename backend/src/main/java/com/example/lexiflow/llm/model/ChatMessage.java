package com.example.lexiflow.llm.model;

public record ChatMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}

