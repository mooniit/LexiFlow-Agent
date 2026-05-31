package com.example.lexiflow.llm.service;

import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.EmbeddingRequest;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.ToolCallRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MockLlmGatewayTest {

    private final MockLlmGateway gateway = new MockLlmGateway();

    // === chat ===

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
    void chatUsesLastUserMessageForResponse() {
        ChatRequest request = new ChatRequest(
                "qa",
                null,
                List.of(
                        new ChatMessage(ChatMessage.Role.SYSTEM, "You are helpful"),
                        new ChatMessage(ChatMessage.Role.USER, "first question"),
                        new ChatMessage(ChatMessage.Role.USER, "second question")
                ),
                Map.of()
        );

        Assertions.assertThat(gateway.chat(request).content()).contains("second question");
    }

    @Test
    void chatReturnsProviderAndModel() {
        ChatRequest request = new ChatRequest("test", "custom-model", List.of(userMsg("hello")), Map.of());

        Assertions.assertThat(gateway.chat(request).provider()).isEqualTo("mock");
        Assertions.assertThat(gateway.chat(request).model()).isEqualTo("custom-model");
    }

    @Test
    void chatDefaultsModelWhenNull() {
        ChatRequest request = new ChatRequest("test", null, List.of(userMsg("hello")), Map.of());

        Assertions.assertThat(gateway.chat(request).model()).isEqualTo("mock-contract-reviewer");
    }

    // === embed ===

    @Test
    void embedReturnsCorrectDimensions() {
        Assertions.assertThat(gateway.embed(new EmbeddingRequest(null, List.of("a", "b"))).embeddings())
                .hasSize(2)
                .allSatisfy(vector -> Assertions.assertThat(vector).hasSize(1536));
    }

    @Test
    void embedReturnsSingleVectorForSingleInput() {
        Assertions.assertThat(gateway.embed(new EmbeddingRequest(null, List.of("test"))).embeddings())
                .hasSize(1);
    }

    @Test
    void embedHandlesEmptyInput() {
        Assertions.assertThat(gateway.embed(new EmbeddingRequest(null, List.of())).embeddings())
                .isEmpty();
    }

    @Test
    void embedHandlesNullTextInList() {
        List<String> texts = new ArrayList<>();
        texts.add("valid");
        texts.add(null);

        Assertions.assertThat(gateway.embed(new EmbeddingRequest(null, texts)).embeddings())
                .hasSize(2);
    }

    @Test
    void embedIsDeterministicForSameInput() {
        var first = gateway.embed(new EmbeddingRequest(null, List.of("hello"))).embeddings().get(0);
        var second = gateway.embed(new EmbeddingRequest(null, List.of("hello"))).embeddings().get(0);

        Assertions.assertThat(first).isEqualTo(second);
    }

    // === tool calling ===

    @Test
    void toolCallingSelectsFirstMockTool() {
        ChatRequest chatRequest = new ChatRequest("tool-call", null, List.of(), Map.of());
        ToolCallRequest request = new ToolCallRequest(
                chatRequest,
                List.of(new ToolCallRequest.ToolDefinition("contract_parse", "Parse contract", Map.of()))
        );

        Assertions.assertThat(gateway.toolCalling(request).toolName()).isEqualTo("contract_parse");
    }

    @Test
    void toolCallingReturnsNoneWhenNoTools() {
        ChatRequest chatRequest = new ChatRequest("tool-call", null, List.of(), Map.of());
        ToolCallRequest request = new ToolCallRequest(chatRequest, List.of());

        Assertions.assertThat(gateway.toolCalling(request).toolName()).isEqualTo("none");
    }

    @Test
    void toolCallingIncludesMockArguments() {
        ChatRequest chatRequest = new ChatRequest("tool-call", null, List.of(), Map.of());
        ToolCallRequest request = new ToolCallRequest(
                chatRequest,
                List.of(new ToolCallRequest.ToolDefinition("risk_analyzer", "Analyze", Map.of()))
        );

        Assertions.assertThat(gateway.toolCalling(request).arguments())
                .containsEntry("mock", true);
    }

    // === structured output ===

    @Test
    void structuredOutputReturnsEnvelope() {
        ChatRequest chatRequest = new ChatRequest(
                "clause-extraction",
                null,
                List.of(new ChatMessage(ChatMessage.Role.USER, "Extract clauses")),
                Map.of()
        );

        Assertions.assertThat(gateway.structuredOutput(new StructuredOutputRequest(chatRequest, Map.of())).data())
                .containsEntry("scenario", "clause-extraction")
                .containsEntry("mock", true);
    }

    // === stream ===

    @Test
    void streamChatTokensInOrder() {
        ChatRequest request = new ChatRequest(
                "stream",
                null,
                List.of(new ChatMessage(ChatMessage.Role.USER, "hello world")),
                Map.of()
        );
        List<String> tokens = new ArrayList<>();

        gateway.streamChat(request, tokens::add);

        Assertions.assertThat(String.join(" ", tokens)).contains("hello world");
    }

    @Test
    void streamChatIncludesUserTextTokens() {
        ChatRequest request = new ChatRequest(
                "stream",
                null,
                List.of(new ChatMessage(ChatMessage.Role.USER, "hello world test")),
                Map.of()
        );
        List<String> tokens = new ArrayList<>();

        gateway.streamChat(request, tokens::add);

        Assertions.assertThat(String.join(" ", tokens)).contains("hello", "world", "test");
    }

    // === edge cases ===

    @Test
    void chatWithEmptyMessagesUsesEmptyString() {
        ChatRequest request = new ChatRequest("test", null, List.of(), Map.of());

        Assertions.assertThat(gateway.chat(request).content()).doesNotContain("null");
    }

    @Test
    void chatWithSystemMessageFallsBackGracefully() {
        ChatRequest request = new ChatRequest(
                "test",
                null,
                List.of(new ChatMessage(ChatMessage.Role.SYSTEM, "You are an assistant")),
                Map.of()
        );

        Assertions.assertThatCode(() -> gateway.chat(request)).doesNotThrowAnyException();
    }

    @Test
    void structuredOutputWithEmptySchema() {
        ChatRequest chatRequest = new ChatRequest("test", null, List.of(userMsg("data")), Map.of());

        Assertions.assertThatCode(() -> gateway.structuredOutput(new StructuredOutputRequest(chatRequest, Collections.emptyMap())))
                .doesNotThrowAnyException();
    }

    // === helpers ===

    private ChatMessage userMsg(String content) {
        return new ChatMessage(ChatMessage.Role.USER, content);
    }
}
