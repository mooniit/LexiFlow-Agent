package com.example.lexiflow.review.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.review.model.AgentStep;
import com.example.lexiflow.review.model.ClauseRisk;
import com.example.lexiflow.review.model.ContractReview;
import com.example.lexiflow.review.service.ContractReviewService;
import com.example.lexiflow.review.service.RiskAnalysisService;
import com.example.lexiflow.review.service.ReviewEventBus;
import com.example.lexiflow.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/reviews")
public class ContractReviewController {

    private final ContractReviewService reviewService;
    private final ReviewEventBus eventBus;
    private final RiskAnalysisService riskAnalysisService;

    public ContractReviewController(ContractReviewService reviewService, ReviewEventBus eventBus,
                                    RiskAnalysisService riskAnalysisService) {
        this.reviewService = reviewService;
        this.eventBus = eventBus;
        this.riskAnalysisService = riskAnalysisService;
    }

    @PostMapping
    public ApiResponse<ContractReview> create(@Valid @RequestBody CreateReviewRequest request,
                                              @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(reviewService.create(request.contractId(), user));
    }

    @GetMapping
    public ApiResponse<List<ContractReview>> list(@RequestParam(required = false) Long contractId) {
        return ApiResponse.ok(reviewService.list(contractId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractReview> detail(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.requireById(id));
    }

    @GetMapping("/{id}/steps")
    public ApiResponse<List<AgentStep>> steps(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.steps(id));
    }

    @GetMapping("/{id}/risks")
    public ApiResponse<List<ClauseRisk>> risks(@PathVariable Long id) {
        reviewService.requireById(id);
        return ApiResponse.ok(riskAnalysisService.listByReview(id));
    }

    @GetMapping("/{id}/report")
    public ApiResponse<ContractReviewService.ReviewReport> report(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.report(id));
    }

    @GetMapping("/{id}/trace")
    public ApiResponse<ContractReviewService.ReviewTrace> trace(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.trace(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ContractReview> cancel(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(reviewService.cancel(id, user));
    }

    @PostMapping("/{id}/rerun")
    public ApiResponse<ContractReview> rerun(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(reviewService.rerun(id, user));
    }

    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long id) {
        reviewService.requireById(id);
        return eventBus.subscribe(id);
    }

    public record CreateReviewRequest(@NotNull Long contractId) {
    }
}
