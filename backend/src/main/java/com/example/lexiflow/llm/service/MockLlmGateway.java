package com.example.lexiflow.llm.service;

import com.example.lexiflow.llm.model.ChatMessage;
import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.ChatResponse;
import com.example.lexiflow.llm.model.EmbeddingRequest;
import com.example.lexiflow.llm.model.EmbeddingResponse;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.model.ToolCallRequest;
import com.example.lexiflow.llm.model.ToolCallResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "lexiflow.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmGateway implements LlmGateway {

    private static final String PROVIDER = "mock";
    private static final String DEFAULT_MODEL = "mock-contract-reviewer";

    @Override
    public ChatResponse chat(ChatRequest request) {
        String lastUserText = request.messages().stream()
                .filter(message -> message.role() == ChatMessage.Role.USER)
                .map(ChatMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        String content = "Mock response for scenario [" + request.scenario() + "]: " + lastUserText;
        return new ChatResponse(content, estimateUsage(request, content), PROVIDER, modelOrDefault(request.model()));
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<String> tokenConsumer) {
        for (String token : chat(request).content().split(" ")) {
            tokenConsumer.accept(token);
        }
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        List<List<Double>> embeddings = new ArrayList<>();
        for (String text : request.texts()) {
            embeddings.add(deterministicEmbedding(text));
        }
        int promptTokens = request.texts().stream().mapToInt(this::roughTokenCount).sum();
        return new EmbeddingResponse(embeddings, new TokenUsage(promptTokens, 0), PROVIDER, modelOrDefault(request.model()));
    }

    @Override
    public StructuredOutputResponse structuredOutput(StructuredOutputRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scenario", request.chatRequest().scenario());
        data.put("summary", chat(request.chatRequest()).content());
        data.put("mock", true);
        return new StructuredOutputResponse(data, estimateUsage(request.chatRequest(), data.toString()), PROVIDER, modelOrDefault(request.chatRequest().model()));
    }

    @Override
    public ToolCallResponse toolCalling(ToolCallRequest request) {
        String toolName = request.tools().isEmpty() ? "none" : request.tools().get(0).name();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("mock", true);
        arguments.put("scenario", request.chatRequest().scenario());
        String message = "Mock selected tool [" + toolName + "]";
        return new ToolCallResponse(
                toolName,
                arguments,
                message,
                estimateUsage(request.chatRequest(), message),
                PROVIDER,
                modelOrDefault(request.chatRequest().model())
        );
    }

    private TokenUsage estimateUsage(ChatRequest request, String completion) {
        int promptTokens = request.messages().stream().map(ChatMessage::content).mapToInt(this::roughTokenCount).sum();
        return new TokenUsage(promptTokens, roughTokenCount(completion));
    }

    private int roughTokenCount(String text) {
        return Math.max(1, text == null ? 0 : text.length() / 4);
    }

    private String modelOrDefault(String model) {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    private List<Double> deterministicEmbedding(String text) {
        List<Double> embedding = new ArrayList<>(16);
        int hash = text == null ? 0 : text.hashCode();
        for (int i = 0; i < 16; i++) {
            int shifted = hash >> (i % 16);
            embedding.add((shifted & 0xFF) / 255.0);
        }
        return embedding;
    }
}
