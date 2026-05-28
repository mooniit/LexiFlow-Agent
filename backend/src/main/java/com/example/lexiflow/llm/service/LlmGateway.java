package com.example.lexiflow.llm.service;

import com.example.lexiflow.llm.model.ChatRequest;
import com.example.lexiflow.llm.model.ChatResponse;
import com.example.lexiflow.llm.model.EmbeddingRequest;
import com.example.lexiflow.llm.model.EmbeddingResponse;
import com.example.lexiflow.llm.model.StructuredOutputRequest;
import com.example.lexiflow.llm.model.StructuredOutputResponse;
import com.example.lexiflow.llm.model.ToolCallRequest;
import com.example.lexiflow.llm.model.ToolCallResponse;
import java.util.function.Consumer;

public interface LlmGateway {

    ChatResponse chat(ChatRequest request);

    void streamChat(ChatRequest request, Consumer<String> tokenConsumer);

    EmbeddingResponse embed(EmbeddingRequest request);

    StructuredOutputResponse structuredOutput(StructuredOutputRequest request);

    ToolCallResponse toolCalling(ToolCallRequest request);
}
