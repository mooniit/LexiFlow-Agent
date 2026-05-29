package com.example.lexiflow.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.approval.mapper.ApprovalHistoryMapper;
import com.example.lexiflow.approval.mapper.ApprovalRequestMapper;
import com.example.lexiflow.approval.model.ApprovalHistory;
import com.example.lexiflow.approval.model.ApprovalRequest;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.review.service.ContractReviewService;
import com.example.lexiflow.review.service.ReviewEventBus;
import com.example.lexiflow.security.CurrentUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final ApprovalRequestMapper approvalRequestMapper;
    private final ApprovalHistoryMapper approvalHistoryMapper;
    private final ReviewEventBus eventBus;
    private final ContractReviewService reviewService;

    public ApprovalService(ApprovalRequestMapper approvalRequestMapper, ApprovalHistoryMapper approvalHistoryMapper,
                           ReviewEventBus eventBus, @Lazy ContractReviewService reviewService) {
        this.approvalRequestMapper = approvalRequestMapper;
        this.approvalHistoryMapper = approvalHistoryMapper;
        this.eventBus = eventBus;
        this.reviewService = reviewService;
    }

    public List<ApprovalRequest> list(String status, Long reviewId) {
        LambdaQueryWrapper<ApprovalRequest> query = new LambdaQueryWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getDeleted, false)
                .orderByDesc(ApprovalRequest::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(ApprovalRequest::getStatus, status);
        }
        if (reviewId != null) {
            query.eq(ApprovalRequest::getReviewId, reviewId);
        }
        return approvalRequestMapper.selectList(query);
    }

    public ApprovalRequest requireById(Long id) {
        ApprovalRequest request = approvalRequestMapper.selectById(id);
        if (request == null || Boolean.TRUE.equals(request.getDeleted())) {
            throw new IllegalArgumentException("Approval request not found: " + id);
        }
        return request;
    }

    public List<ApprovalHistory> history(Long approvalId) {
        requireById(approvalId);
        return approvalHistoryMapper.selectList(new LambdaQueryWrapper<ApprovalHistory>()
                .eq(ApprovalHistory::getApprovalRequestId, approvalId)
                .orderByAsc(ApprovalHistory::getCreatedAt));
    }

    @Transactional
    public ApprovalRequest ensurePendingReviewApproval(Long reviewId, List<ClauseRisk> risks, CurrentUser user) {
        List<ApprovalRequest> existing = approvalRequestMapper.selectList(new LambdaQueryWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getReviewId, reviewId)
                .eq(ApprovalRequest::getStatus, "PENDING")
                .eq(ApprovalRequest::getDeleted, false)
                .orderByDesc(ApprovalRequest::getCreatedAt));
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        ApprovalRequest request = new ApprovalRequest();
        request.setReviewId(reviewId);
        request.setApprovalType("REVIEW");
        request.setStatus("PENDING");
        request.setRequestedBy(user.id());
        request.setRiskSummary(toRiskSummary(risks));
        request.setCreatedBy(user.id());
        request.setUpdatedBy(user.id());
        approvalRequestMapper.insert(request);
        addHistory(request.getId(), "SUBMIT", user.id(), "High-risk review requires manual approval.", "{\"source\":\"agent\"}");
        eventBus.publish(reviewId, "APPROVAL_REQUIRED", "High-risk review requires manual approval.");
        return request;
    }

    @Transactional
    public ApprovalRequest approve(Long id, String comment, CurrentUser user) {
        ApprovalRequest request = resolve(id, "APPROVED", comment, user);
        reviewService.resumeAfterApproval(request.getReviewId(), user);
        return request;
    }

    @Transactional
    public ApprovalRequest reject(Long id, String comment, CurrentUser user) {
        ApprovalRequest request = resolve(id, "REJECTED", comment, user);
        reviewService.rejectAfterApproval(request.getReviewId(), user, safeComment(comment));
        return request;
    }

    @Transactional
    public ApprovalRequest requestRevision(Long id, String comment, CurrentUser user) {
        ApprovalRequest request = resolve(id, "REVISION_REQUESTED", comment, user);
        reviewService.rejectAfterApproval(request.getReviewId(), user, "Revision requested: " + safeComment(comment));
        return request;
    }

    @Transactional
    public ApprovalRequest escalate(Long id, Long approverId, String comment, CurrentUser user) {
        ApprovalRequest request = requirePending(id);
        request.setApproverId(approverId);
        request.setComment(comment);
        request.setUpdatedBy(user.id());
        approvalRequestMapper.updateById(request);
        addHistory(request.getId(), "ESCALATE", user.id(), comment, "{\"approverId\":" + approverId + "}");
        eventBus.publish(request.getReviewId(), "APPROVAL_ESCALATED", "Approval request escalated.");
        return request;
    }

    private ApprovalRequest resolve(Long id, String status, String comment, CurrentUser user) {
        ApprovalRequest request = requirePending(id);
        request.setStatus(status);
        request.setApproverId(user.id());
        request.setComment(comment);
        request.setResolvedAt(OffsetDateTime.now());
        request.setUpdatedBy(user.id());
        approvalRequestMapper.updateById(request);
        addHistory(request.getId(), toAction(status), user.id(), comment, "{}");
        eventBus.publish(request.getReviewId(), "APPROVAL_" + status, "Approval request " + status.toLowerCase() + ".");
        return request;
    }

    private ApprovalRequest requirePending(Long id) {
        ApprovalRequest request = requireById(id);
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Only pending approval requests can be processed.");
        }
        return request;
    }

    private void addHistory(Long requestId, String action, Long operatorId, String comment, String metadata) {
        ApprovalHistory history = new ApprovalHistory();
        history.setApprovalRequestId(requestId);
        history.setAction(action);
        history.setOperatorId(operatorId);
        history.setComment(comment);
        history.setMetadata(metadata);
        history.setCreatedBy(operatorId);
        approvalHistoryMapper.insert(history);
    }

    private String toRiskSummary(List<ClauseRisk> risks) {
        String riskItems = risks.stream()
                .filter(risk -> "HIGH".equals(risk.getRiskLevel()) || Boolean.TRUE.equals(risk.getRequiresApproval()))
                .map(risk -> "{\"riskId\":" + risk.getId()
                        + ",\"riskLevel\":" + JsonStrings.quote(risk.getRiskLevel())
                        + ",\"riskType\":" + JsonStrings.quote(risk.getRiskType())
                        + ",\"clauseName\":" + JsonStrings.quote(risk.getClauseName())
                        + ",\"reason\":" + JsonStrings.quote(risk.getReason()) + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "{\"trigger\":\"HIGH_RISK\",\"risks\":[" + riskItems + "]}";
    }

    private String toAction(String status) {
        if ("APPROVED".equals(status)) {
            return "APPROVE";
        }
        if ("REVISION_REQUESTED".equals(status)) {
            return "REQUEST_REVISION";
        }
        return "REJECT";
    }

    private String safeComment(String comment) {
        return comment == null || comment.isBlank() ? "No approval comment provided." : comment;
    }
}
