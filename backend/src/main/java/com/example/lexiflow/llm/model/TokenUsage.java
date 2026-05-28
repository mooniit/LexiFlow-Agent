package com.example.lexiflow.llm.model;

public record TokenUsage(int promptTokens, int completionTokens) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}

