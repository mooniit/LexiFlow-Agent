package com.example.lexiflow.llm.service;

import com.example.lexiflow.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DeepSeekLlmGatewayTest {

    @Test
    void requiresDeepSeekApiKeyWhenProviderIsDeepSeek() {
        LlmProperties properties = new LlmProperties(
                "deepseek",
                new LlmProperties.DeepSeekProperties("https://api.deepseek.com", "", "deepseek-v4-flash"),
                new LlmProperties.DashScopeProperties("https://dashscope.aliyuncs.com/compatible-mode/v1", "dashscope-key", "text-embedding-v4", null)
        );

        Assertions.assertThatThrownBy(() -> new DeepSeekLlmGateway(properties, new ObjectMapper(), RestClient.builder()))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void requiresDashScopeApiKeyWhenProviderIsDeepSeek() {
        LlmProperties properties = new LlmProperties(
                "deepseek",
                new LlmProperties.DeepSeekProperties("https://api.deepseek.com", "deepseek-key", "deepseek-v4-flash"),
                new LlmProperties.DashScopeProperties("https://dashscope.aliyuncs.com/compatible-mode/v1", "", "text-embedding-v4", null)
        );

        Assertions.assertThatThrownBy(() -> new DeepSeekLlmGateway(properties, new ObjectMapper(), RestClient.builder()))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("DASHSCOPE_API_KEY");
    }
}
