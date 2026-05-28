package com.example.lexiflow.llm.model;

import java.util.List;

public record EmbeddingRequest(String model, List<String> texts) {
}

