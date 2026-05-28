package com.example.lexiflow.llm.service;

import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.EmbeddingRequest;
import com.example.lexiflow.llm.model.ToolCallRequest;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MockLlmGatewayTest {

    private final MockLlmGateway gateway = new MockLlmGateway();

    @Test
    void returnsDeterministicChatResponse() {
        ChatRequest request = new ChatRequest(
                "risk-analysis",
                null,
                List.of(new ChatMessage(ChatMessage.Role.USER, "Check payment term")),
                Map.of()
        );

        Assertions.assertThat(gateway.chat(request).content()).contains("risk-analysis", "Check payment term");
    }

    @Test
    void returnsEmbeddingForEachText() {
        Assertions.assertThat(gateway.embed(new EmbeddingRequest(null, List.of("a", "b"))).embeddings())
                .hasSize(2)
                .allSatisfy(vector -> Assertions.assertThat(vector).hasSize(16));
    }

    @Test
    void selectsFirstMockTool() {
        ChatRequest chatRequest = new ChatRequest("tool-call", null, List.of(), Map.of());
        ToolCallRequest request = new ToolCallRequest(
                chatRequest,
                List.of(new ToolCallRequest.ToolDefinition("contract_parse", "Parse contract", Map.of()))
        );

        Assertions.assertThat(gateway.toolCalling(request).toolName()).isEqualTo("contract_parse");
    }
}
