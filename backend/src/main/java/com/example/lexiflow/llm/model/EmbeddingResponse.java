package com.example.lexiflow.llm.model;

import java.util.List;

public record EmbeddingResponse(List<List<Double>> embeddings, TokenUsage usage, String provider, String model) {
}

