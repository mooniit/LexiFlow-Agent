package com.example.lexiflow.tool.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.model.ReviewToolConfig;
import com.example.lexiflow.tool.service.ReviewToolConfigService;
import com.example.lexiflow.tool.service.ReviewToolConfigService.SaveToolCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/review-tools")
public class ReviewToolConfigController {

    private final ReviewToolConfigService toolConfigService;

    public ReviewToolConfigController(ReviewToolConfigService toolConfigService) {
        this.toolConfigService = toolConfigService;
    }

    @GetMapping
    public ApiResponse<List<ReviewToolConfig>> list(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(toolConfigService.list(user));
    }

    @PostMapping
    public ApiResponse<ReviewToolConfig> create(@Valid @RequestBody SaveToolRequest request,
                                                @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(toolConfigService.create(request.toCommand(), user));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReviewToolConfig> update(@PathVariable Long id,
                                                @Valid @RequestBody SaveToolRequest request,
                                                @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(toolConfigService.update(id, request.toCommand(), user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        toolConfigService.delete(id, user);
        return ApiResponse.ok(null);
    }

    public record SaveToolRequest(@NotBlank String toolName, String requiredPermission, String riskLevel,
                                  Boolean approvalRequired, Boolean enabled) {
        SaveToolCommand toCommand() {
            return new SaveToolCommand(toolName, requiredPermission, riskLevel, approvalRequired, enabled);
        }
    }
}
