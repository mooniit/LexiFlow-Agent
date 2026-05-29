package com.example.lexiflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexiflow.llm")
public record LlmProperties(
        String provider,
        DeepSeekProperties deepseek,
        DashScopeProperties dashscope
) {

    public record DeepSeekProperties(
            String baseUrl,
            String apiKey,
            String chatModel
    ) {
    }

    public record DashScopeProperties(
            String baseUrl,
            String apiKey,
            String embeddingModel,
            Integer embeddingDimensions
    ) {
    }
}
