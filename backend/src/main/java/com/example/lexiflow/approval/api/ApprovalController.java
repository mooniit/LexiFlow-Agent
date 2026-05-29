package com.example.lexiflow.approval.api;

import com.example.lexiflow.approval.model.ApprovalHistory;
import com.example.lexiflow.approval.model.ApprovalRequest;
import com.example.lexiflow.approval.service.ApprovalService;
import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ApiResponse<List<ApprovalRequest>> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long reviewId) {
        return ApiResponse.ok(approvalService.list(status, reviewId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApprovalRequest> detail(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.requireById(id));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<ApprovalHistory>> history(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.history(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ApprovalRequest> approve(@PathVariable Long id, @Valid @RequestBody ApprovalActionRequest request,
                                                @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(approvalService.approve(id, request.comment(), user));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ApprovalRequest> reject(@PathVariable Long id, @Valid @RequestBody ApprovalActionRequest request,
                                               @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(approvalService.reject(id, request.comment(), user));
    }

    @PostMapping("/{id}/request-revision")
    public ApiResponse<ApprovalRequest> requestRevision(@PathVariable Long id,
                                                        @Valid @RequestBody ApprovalActionRequest request,
                                                        @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(approvalService.requestRevision(id, request.comment(), user));
    }

    @PostMapping("/{id}/escalate")
    public ApiResponse<ApprovalRequest> escalate(@PathVariable Long id, @Valid @RequestBody EscalateRequest request,
                                                 @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(approvalService.escalate(id, request.approverId(), request.comment(), user));
    }

    public record ApprovalActionRequest(String comment) {
    }

    public record EscalateRequest(Long approverId, String comment) {
    }
}
