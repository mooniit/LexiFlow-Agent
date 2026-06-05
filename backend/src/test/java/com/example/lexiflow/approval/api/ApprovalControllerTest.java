package com.example.lexiflow.approval.api;

import com.example.lexiflow.approval.model.ApprovalRequest;
import com.example.lexiflow.approval.service.ApprovalEventMessage;
import com.example.lexiflow.approval.service.ApprovalService;
import com.example.lexiflow.review.service.ContractReviewService;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApprovalControllerTest {

    @Test
    void approveSynchronouslyAppliesApprovalEventAfterResolvingApproval() {
        ApprovalService approvalService = Mockito.mock(ApprovalService.class);
        ContractReviewService reviewService = Mockito.mock(ContractReviewService.class);
        ApprovalRequest approved = approval(7L, 99L, "APPROVED");
        CurrentUser user = user();
        Mockito.when(approvalService.approve(7L, "ok", user)).thenReturn(approved);

        ApprovalController controller = new ApprovalController(approvalService, reviewService);

        controller.approve(7L, new ApprovalController.ApprovalActionRequest("ok"), user);

        var captor = org.mockito.ArgumentCaptor.forClass(ApprovalEventMessage.class);
        Mockito.verify(reviewService).handleApprovalEvent(captor.capture());
        ApprovalEventMessage message = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(message.approvalRequestId()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(message.reviewId()).isEqualTo(99L);
        org.assertj.core.api.Assertions.assertThat(message.operatorId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(message.action()).isEqualTo("APPROVED");
        org.assertj.core.api.Assertions.assertThat(message.comment()).isEqualTo("ok");
    }

    @Test
    void rejectSynchronouslyAppliesApprovalEventAfterResolvingApproval() {
        ApprovalService approvalService = Mockito.mock(ApprovalService.class);
        ContractReviewService reviewService = Mockito.mock(ContractReviewService.class);
        ApprovalRequest rejected = approval(7L, 99L, "REJECTED");
        CurrentUser user = user();
        Mockito.when(approvalService.reject(7L, "no", user)).thenReturn(rejected);

        ApprovalController controller = new ApprovalController(approvalService, reviewService);

        controller.reject(7L, new ApprovalController.ApprovalActionRequest("no"), user);

        var captor = org.mockito.ArgumentCaptor.forClass(ApprovalEventMessage.class);
        Mockito.verify(reviewService).handleApprovalEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().action()).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().comment()).isEqualTo("no");
    }

    private ApprovalRequest approval(Long id, Long reviewId, String status) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(id);
        request.setReviewId(reviewId);
        request.setStatus(status);
        return request;
    }

    private CurrentUser user() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("review:write"), true);
    }
}
