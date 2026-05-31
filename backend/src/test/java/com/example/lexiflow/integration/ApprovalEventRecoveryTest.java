package com.example.lexiflow.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.agent.model.AgentTaskStatus;
import com.example.lexiflow.approval.mapper.ApprovalRequestMapper;
import com.example.lexiflow.approval.model.ApprovalRequest;
import com.example.lexiflow.approval.service.ApprovalService;
import com.example.lexiflow.contract.mapper.ContractMapper;
import com.example.lexiflow.contract.model.Contract;
import com.example.lexiflow.review.mapper.ContractReviewMapper;
import com.example.lexiflow.review.model.ContractReview;
import com.example.lexiflow.review.service.ContractReviewService;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ApprovalEventRecoveryTest extends TestcontainersBaseTest {

    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private ApprovalRequestMapper approvalRequestMapper;
    @Autowired
    private ContractReviewService reviewService;
    @Autowired
    private ContractReviewMapper reviewMapper;
    @Autowired
    private ContractMapper contractMapper;

    private Long reviewId;

    @BeforeEach
    void setUp() {
        Contract contract = new Contract();
        contract.setContractName("审批测试合同");
        contract.setContractType("SALES");
        contract.setFileType("txt");
        contract.setFilePath("test/approval.txt");
        contract.setOriginalFilename("approval.txt");
        contract.setStatus("UPLOADED");
        contract.setMetadata("{}");
        contractMapper.insert(contract);
        ContractReview review = reviewService.create(contract.getId(), testUser());
        reviewId = review.getId();
    }

    @Test
    void createsPendingApprovalRequest() {
        var risks = List.<com.example.lexiflow.review.model.ClauseRisk>of();
        ApprovalRequest request = approvalService.ensurePendingReviewApproval(reviewId, risks, testUser());

        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getStatus()).isEqualTo("PENDING");
        Assertions.assertThat(request.getReviewId()).isEqualTo(reviewId);
    }

    @Test
    void approvalHistoryIsRecorded() {
        ApprovalRequest request = approvalService.ensurePendingReviewApproval(reviewId, List.of(), testUser());

        var history = approvalService.history(request.getId());

        Assertions.assertThat(history).isNotEmpty();
        Assertions.assertThat(history.get(0).getAction()).isEqualTo("SUBMIT");
    }

    @Test
    void approveResolvesRequestAndTransitionsReview() {
        ApprovalRequest request = approvalService.ensurePendingReviewApproval(reviewId, List.of(), testUser());
        ContractReview review = reviewMapper.selectById(reviewId);
        review.setStatus(AgentTaskStatus.WAITING_APPROVAL.name());
        reviewMapper.updateById(review);

        ApprovalRequest resolved = approvalService.approve(request.getId(), "Approved by legal manager", testUser());

        Assertions.assertThat(resolved.getStatus()).isEqualTo("APPROVED");
        Assertions.assertThat(resolved.getApproverId()).isEqualTo(testUser().id());
    }

    @Test
    void rejectResolvesRequestWithReason() {
        ApprovalRequest request = approvalService.ensurePendingReviewApproval(reviewId, List.of(), testUser());
        ContractReview review = reviewMapper.selectById(reviewId);
        review.setStatus(AgentTaskStatus.WAITING_APPROVAL.name());
        reviewMapper.updateById(review);

        ApprovalRequest resolved = approvalService.reject(request.getId(), "Risk too high, needs renegotiation", testUser());

        Assertions.assertThat(resolved.getStatus()).isEqualTo("REJECTED");
        Assertions.assertThat(resolved.getComment()).contains("renegotiation");
    }

    @Test
    void requestRevisionWithComment() {
        ApprovalRequest request = approvalService.ensurePendingReviewApproval(reviewId, List.of(), testUser());
        ContractReview review = reviewMapper.selectById(reviewId);
        review.setStatus(AgentTaskStatus.WAITING_APPROVAL.name());
        reviewMapper.updateById(review);

        ApprovalRequest resolved = approvalService.requestRevision(request.getId(), "Please add data protection clause", testUser());

        Assertions.assertThat(resolved.getStatus()).isEqualTo("REVISION_REQUESTED");
        Assertions.assertThat(resolved.getComment()).contains("data protection");
    }

    @Test
    void onlyPendingRequestsCanBeResolved() {
        ApprovalRequest request = approvalService.ensurePendingReviewApproval(reviewId, List.of(), testUser());
        ContractReview review = reviewMapper.selectById(reviewId);
        review.setStatus(AgentTaskStatus.WAITING_APPROVAL.name());
        reviewMapper.updateById(review);
        approvalService.approve(request.getId(), "ok", testUser());

        Assertions.assertThatThrownBy(() -> approvalService.approve(request.getId(), "double approve", testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only pending");
    }

    private CurrentUser testUser() {
        return new CurrentUser(1L, "tester", "Test User", null, List.of("LEGAL_MANAGER"),
                List.of("approval:read", "approval:write"), true);
    }
}
