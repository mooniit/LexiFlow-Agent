package com.example.lexiflow.observability.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.observability.service.ObservabilityService;
import com.example.lexiflow.observability.service.ObservabilityService.ObservabilitySummary;
import com.example.lexiflow.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/observability")
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    public ObservabilityController(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @GetMapping("/summary")
    public ApiResponse<ObservabilitySummary> summary(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(observabilityService.summary(user));
    }
}
