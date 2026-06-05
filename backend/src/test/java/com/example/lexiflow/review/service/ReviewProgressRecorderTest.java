package com.example.lexiflow.review.service;

import com.example.lexiflow.agent.model.AgentStepType;
import com.example.lexiflow.agent.model.AgentTaskStatus;
import com.example.lexiflow.agent.service.AgentStateMachine;
import com.example.lexiflow.review.mapper.AgentStateTransitionLogMapper;
import com.example.lexiflow.review.mapper.AgentStepMapper;
import com.example.lexiflow.review.mapper.ContractReviewMapper;
import com.example.lexiflow.review.model.AgentStateTransitionLog;
import com.example.lexiflow.review.model.AgentStep;
import com.example.lexiflow.review.model.ContractReview;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ReviewProgressRecorderTest {

    @Test
    void recordsRunningStepAndCompletesItWithRealDuration() {
        AgentStepMapper stepMapper = Mockito.mock(AgentStepMapper.class);
        ReviewProgressRecorder recorder = recorder(stepMapper);

        AgentStep runningStep = recorder.beginStep(11L, AgentStepType.CLAUSE_EXTRACTION, "{\"contractId\":2}", 1L);

        ArgumentCaptor<AgentStep> insertCaptor = ArgumentCaptor.forClass(AgentStep.class);
        Mockito.verify(stepMapper).insert(insertCaptor.capture());
        Assertions.assertThat(runningStep).isSameAs(insertCaptor.getValue());
        Assertions.assertThat(runningStep.getStatus()).isEqualTo("RUNNING");
        Assertions.assertThat(runningStep.getStartedAt()).isNotNull();
        Assertions.assertThat(runningStep.getFinishedAt()).isNull();

        runningStep.setId(7L);
        runningStep.setStartedAt(OffsetDateTime.now().minus(Duration.ofMillis(25)));
        Mockito.when(stepMapper.selectById(7L)).thenReturn(runningStep);

        recorder.completeStep(7L, "{\"clauseCount\":3}", 1L);

        ArgumentCaptor<AgentStep> updateCaptor = ArgumentCaptor.forClass(AgentStep.class);
        Mockito.verify(stepMapper).updateById(updateCaptor.capture());
        AgentStep completed = updateCaptor.getValue();
        Assertions.assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        Assertions.assertThat(completed.getFinishedAt()).isAfter(completed.getStartedAt());
        Assertions.assertThat(completed.getOutputSummary()).isEqualTo("{\"clauseCount\":3}");
    }

    @Test
    void transitionCommitsReviewStateAndTransitionLogWithUpdatedTimestamp() {
        ContractReviewMapper reviewMapper = Mockito.mock(ContractReviewMapper.class);
        AgentStateTransitionLogMapper transitionMapper = Mockito.mock(AgentStateTransitionLogMapper.class);
        ContractReview review = new ContractReview();
        review.setId(12L);
        review.setStatus(AgentTaskStatus.CREATED.name());
        Mockito.when(reviewMapper.selectById(12L)).thenReturn(review);

        ReviewProgressRecorder recorder = new ReviewProgressRecorder(
                reviewMapper,
                Mockito.mock(AgentStepMapper.class),
                transitionMapper,
                new AgentStateMachine(),
                Mockito.mock(ReviewEventBus.class)
        );

        ContractReview updated = recorder.transition(12L, AgentTaskStatus.PARSING, "Start parsing.", 1L, 10);

        Assertions.assertThat(updated.getStatus()).isEqualTo(AgentTaskStatus.PARSING.name());
        Assertions.assertThat(updated.getProgressPercent()).isEqualTo(10);
        Assertions.assertThat(updated.getUpdatedAt()).isNotNull();
        Mockito.verify(reviewMapper).updateById(updated);

        ArgumentCaptor<AgentStateTransitionLog> transitionCaptor = ArgumentCaptor.forClass(AgentStateTransitionLog.class);
        Mockito.verify(transitionMapper).insert(transitionCaptor.capture());
        Assertions.assertThat(transitionCaptor.getValue().getFromStatus()).isEqualTo(AgentTaskStatus.CREATED.name());
        Assertions.assertThat(transitionCaptor.getValue().getToStatus()).isEqualTo(AgentTaskStatus.PARSING.name());
    }

    private ReviewProgressRecorder recorder(AgentStepMapper stepMapper) {
        return new ReviewProgressRecorder(
                Mockito.mock(ContractReviewMapper.class),
                stepMapper,
                Mockito.mock(AgentStateTransitionLogMapper.class),
                new AgentStateMachine(),
                Mockito.mock(ReviewEventBus.class)
        );
    }
}
