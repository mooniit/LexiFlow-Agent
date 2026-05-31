package com.example.lexiflow.prompt.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.prompt.model.PromptTemplate;
import com.example.lexiflow.prompt.service.PromptTemplateService;
import com.example.lexiflow.prompt.service.PromptTemplateService.RenderedPrompt;
import com.example.lexiflow.prompt.service.PromptTemplateService.UpsertPromptTemplateRequest;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/prompts")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public ApiResponse<List<PromptTemplate>> list(@RequestParam(required = false) String scene,
                                                  @RequestParam(required = false) Boolean enabled,
                                                  @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(promptTemplateService.list(scene, enabled, user));
    }

    @PostMapping
    public ApiResponse<PromptTemplate> create(@RequestBody UpsertPromptTemplateRequest request,
                                              @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(promptTemplateService.create(request, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromptTemplate> update(@PathVariable Long id,
                                              @RequestBody UpsertPromptTemplateRequest request,
                                              @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(promptTemplateService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        promptTemplateService.delete(id, user);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/preview")
    public ApiResponse<RenderedPrompt> preview(@PathVariable Long id,
                                               @RequestBody Map<String, String> variables,
                                               @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(promptTemplateService.preview(id, variables, user));
    }
}
