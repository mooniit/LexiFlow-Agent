package com.example.lexiflow.rag.service;

public class KnowledgeAccessDeniedException extends RuntimeException {

    public KnowledgeAccessDeniedException(String message) {
        super(message);
    }
}

