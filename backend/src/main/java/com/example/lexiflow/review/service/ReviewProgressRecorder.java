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
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReviewProgressRecorder {

    private final ContractReviewMapper reviewMapper;
    private final AgentStepMapper stepMapper;
    private final AgentStateTransitionLogMapper transitionLogMapper;
    private final AgentStateMachine stateMachine;
    private final ReviewEventBus eventBus;

    public ReviewProgressRecorder(ContractReviewMapper reviewMapper, AgentStepMapper stepMapper,
                                  AgentStateTransitionLogMapper transitionLogMapper,
                                  AgentStateMachine stateMachine, ReviewEventBus eventBus) {
        this.reviewMapper = reviewMapper;
        this.stepMapper = stepMapper;
        this.transitionLogMapper = transitionLogMapper;
        this.stateMachine = stateMachine;
        this.eventBus = eventBus;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ContractReview transition(Long reviewId, AgentTaskStatus to, String reason, Long userId, int progress) {
        ContractReview review = reviewMapper.selectById(reviewId);
        if (review == null || Boolean.TRUE.equals(review.getDeleted())) {
            throw new IllegalArgumentException("Review not found: " + reviewId);
        }
        AgentTaskStatus from = AgentTaskStatus.valueOf(review.getStatus());
        stateMachine.assertCanTransit(from, to);
        review.setStatus(to.name());
        review.setProgressPercent(progress);
        review.setUpdatedBy(userId);
        review.setUpdatedAt(OffsetDateTime.now());
        reviewMapper.updateById(review);

        AgentStateTransitionLog log = new AgentStateTransitionLog();
        log.setReviewId(reviewId);
        log.setFromStatus(from.name());
        log.setToStatus(to.name());
        log.setReason(reason);
        log.setCreatedBy(userId);
        transitionLogMapper.insert(log);
        publishAfterCommit(reviewId, to.name(), reason);
        return review;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentStep beginStep(Long reviewId, AgentStepType stepType, String input, Long userId) {
        AgentStep step = new AgentStep();
        step.setReviewId(reviewId);
        step.setStepType(stepType.name());
        step.setStatus("RUNNING");
        step.setInputSummary(input == null ? "{}" : input);
        step.setOutputSummary("{}");
        step.setStartedAt(OffsetDateTime.now());
        step.setCreatedBy(userId);
        step.setUpdatedBy(userId);
        stepMapper.insert(step);
        publishAfterCommit(reviewId, stepType.name(), stepType.name() + " started.");
        return step;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeStep(Long stepId, String output, Long userId) {
        AgentStep step = requireStep(stepId);
        step.setStatus("COMPLETED");
        step.setOutputSummary(output == null ? "{}" : output);
        step.setFinishedAt(OffsetDateTime.now());
        step.setUpdatedBy(userId);
        stepMapper.updateById(step);
        publishAfterCommit(step.getReviewId(), step.getStepType(), step.getStepType() + " completed.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStep(Long stepId, String error, Long userId) {
        AgentStep step = requireStep(stepId);
        step.setStatus("FAILED");
        step.setErrorMessage(error);
        step.setFinishedAt(OffsetDateTime.now());
        step.setUpdatedBy(userId);
        stepMapper.updateById(step);
        publishAfterCommit(step.getReviewId(), step.getStepType(), step.getStepType() + " failed.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ContractReview updateReviewResult(Long reviewId, String overallRiskLevel, String resultSummary, Long userId) {
        ContractReview review = reviewMapper.selectById(reviewId);
        if (review == null || Boolean.TRUE.equals(review.getDeleted())) {
            throw new IllegalArgumentException("Review not found: " + reviewId);
        }
        review.setOverallRiskLevel(overallRiskLevel);
        review.setResultSummary(resultSummary);
        review.setUpdatedBy(userId);
        review.setUpdatedAt(OffsetDateTime.now());
        reviewMapper.updateById(review);
        return review;
    }

    private AgentStep requireStep(Long stepId) {
        AgentStep step = stepMapper.selectById(stepId);
        if (step == null || Boolean.TRUE.equals(step.getDeleted())) {
            throw new IllegalArgumentException("Agent step not found: " + stepId);
        }
        return step;
    }

    private void publishAfterCommit(Long reviewId, String type, String message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventBus.publish(reviewId, type, message);
                }
            });
        } else {
            eventBus.publish(reviewId, type, message);
        }
    }
}
