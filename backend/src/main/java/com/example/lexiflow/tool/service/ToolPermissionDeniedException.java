package com.example.lexiflow.tool.service;

public class ToolPermissionDeniedException extends RuntimeException {

    public ToolPermissionDeniedException(String message) {
        super(message);
    }
}

