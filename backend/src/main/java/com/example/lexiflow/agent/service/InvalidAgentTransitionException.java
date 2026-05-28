package com.example.lexiflow.agent.service;

import com.example.lexiflow.agent.model.AgentTaskStatus;

public class InvalidAgentTransitionException extends RuntimeException {

    public InvalidAgentTransitionException(AgentTaskStatus from, AgentTaskStatus to) {
        super("Illegal agent task transition: " + from + " -> " + to);
    }
}

