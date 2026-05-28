package com.example.lexiflow.agent.service;

import com.example.lexiflow.agent.model.AgentTaskStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentStateMachine {

    private final Map<AgentTaskStatus, Set<AgentTaskStatus>> transitions = new EnumMap<>(AgentTaskStatus.class);

    public AgentStateMachine() {
        transitions.put(AgentTaskStatus.CREATED, EnumSet.of(
                AgentTaskStatus.PARSING,
                AgentTaskStatus.CANCELLED,
                AgentTaskStatus.FAILED
        ));
        transitions.put(AgentTaskStatus.PARSING, EnumSet.of(
                AgentTaskStatus.EXTRACTING,
                AgentTaskStatus.FAILED,
                AgentTaskStatus.CANCELLED
        ));
        transitions.put(AgentTaskStatus.EXTRACTING, EnumSet.of(
                AgentTaskStatus.RETRIEVING_RULES,
                AgentTaskStatus.FAILED,
                AgentTaskStatus.CANCELLED
        ));
        transitions.put(AgentTaskStatus.RETRIEVING_RULES, EnumSet.of(
                AgentTaskStatus.ANALYZING,
                AgentTaskStatus.FAILED,
                AgentTaskStatus.CANCELLED
        ));
        transitions.put(AgentTaskStatus.ANALYZING, EnumSet.of(
                AgentTaskStatus.WAITING_APPROVAL,
                AgentTaskStatus.GENERATING_REPORT,
                AgentTaskStatus.FAILED,
                AgentTaskStatus.CANCELLED
        ));
        transitions.put(AgentTaskStatus.WAITING_APPROVAL, EnumSet.of(
                AgentTaskStatus.GENERATING_REPORT,
                AgentTaskStatus.FAILED,
                AgentTaskStatus.CANCELLED
        ));
        transitions.put(AgentTaskStatus.GENERATING_REPORT, EnumSet.of(
                AgentTaskStatus.COMPLETED,
                AgentTaskStatus.FAILED,
                AgentTaskStatus.CANCELLED
        ));
        transitions.put(AgentTaskStatus.COMPLETED, EnumSet.noneOf(AgentTaskStatus.class));
        transitions.put(AgentTaskStatus.FAILED, EnumSet.of(AgentTaskStatus.PARSING, AgentTaskStatus.CANCELLED));
        transitions.put(AgentTaskStatus.CANCELLED, EnumSet.noneOf(AgentTaskStatus.class));
    }

    public boolean canTransit(AgentTaskStatus from, AgentTaskStatus to) {
        return transitions.getOrDefault(from, Set.of()).contains(to);
    }

    public void assertCanTransit(AgentTaskStatus from, AgentTaskStatus to) {
        if (!canTransit(from, to)) {
            throw new InvalidAgentTransitionException(from, to);
        }
    }

    public Set<AgentTaskStatus> nextStatuses(AgentTaskStatus from) {
        return Set.copyOf(transitions.getOrDefault(from, Set.of()));
    }
}

