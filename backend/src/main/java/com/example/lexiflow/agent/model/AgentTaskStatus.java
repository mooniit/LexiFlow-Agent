package com.example.lexiflow.agent.model;

public enum AgentTaskStatus {
    CREATED,
    PARSING,
    EXTRACTING,
    RETRIEVING_RULES,
    ANALYZING,
    WAITING_APPROVAL,
    GENERATING_REPORT,
    COMPLETED,
    FAILED,
    CANCELLED
}

