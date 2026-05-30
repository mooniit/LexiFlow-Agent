package com.example.lexiflow.approval.service;

import com.example.lexiflow.infrastructure.mq.RabbitMqNames;
import com.example.lexiflow.review.service.ContractReviewService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ApprovalEventConsumer {

    private final ContractReviewService reviewService;

    public ApprovalEventConsumer(ContractReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @RabbitListener(queues = RabbitMqNames.APPROVAL_EVENT_QUEUE)
    public void consume(ApprovalEventMessage message) {
        reviewService.handleApprovalEvent(message);
    }
}
