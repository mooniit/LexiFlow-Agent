package com.example.lexiflow.agent.service;

import static com.example.lexiflow.agent.model.AgentTaskStatus.ANALYZING;
import static com.example.lexiflow.agent.model.AgentTaskStatus.CANCELLED;
import static com.example.lexiflow.agent.model.AgentTaskStatus.COMPLETED;
import static com.example.lexiflow.agent.model.AgentTaskStatus.CREATED;
import static com.example.lexiflow.agent.model.AgentTaskStatus.EXTRACTING;
import static com.example.lexiflow.agent.model.AgentTaskStatus.GENERATING_REPORT;
import static com.example.lexiflow.agent.model.AgentTaskStatus.PARSING;
import static com.example.lexiflow.agent.model.AgentTaskStatus.RETRIEVING_RULES;
import static com.example.lexiflow.agent.model.AgentTaskStatus.WAITING_APPROVAL;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentStateMachineTest {

    private final AgentStateMachine stateMachine = new AgentStateMachine();

    @Test
    void allowsMainReviewPath() {
        Assertions.assertThat(stateMachine.canTransit(CREATED, PARSING)).isTrue();
        Assertions.assertThat(stateMachine.canTransit(PARSING, EXTRACTING)).isTrue();
        Assertions.assertThat(stateMachine.canTransit(EXTRACTING, RETRIEVING_RULES)).isTrue();
        Assertions.assertThat(stateMachine.canTransit(RETRIEVING_RULES, ANALYZING)).isTrue();
        Assertions.assertThat(stateMachine.canTransit(ANALYZING, GENERATING_REPORT)).isTrue();
        Assertions.assertThat(stateMachine.canTransit(GENERATING_REPORT, COMPLETED)).isTrue();
    }

    @Test
    void allowsApprovalPauseAndResume() {
        Assertions.assertThat(stateMachine.canTransit(ANALYZING, WAITING_APPROVAL)).isTrue();
        Assertions.assertThat(stateMachine.canTransit(WAITING_APPROVAL, GENERATING_REPORT)).isTrue();
    }

    @Test
    void blocksTerminalTransitions() {
        Assertions.assertThatThrownBy(() -> stateMachine.assertCanTransit(COMPLETED, PARSING))
                .isInstanceOf(InvalidAgentTransitionException.class);
        Assertions.assertThat(stateMachine.canTransit(CANCELLED, PARSING)).isFalse();
    }
}

