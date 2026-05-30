package com.example.lexiflow.approval.service;

public record ApprovalEventMessage(
        Long approvalRequestId,
        Long reviewId,
        Long operatorId,
        String operatorUsername,
        String action,
        String comment
) {
}
