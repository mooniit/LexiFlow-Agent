package com.example.lexiflow.rag.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.rag.model.QaHistory;
import com.example.lexiflow.rag.service.KnowledgeQaService;
import com.example.lexiflow.rag.service.KnowledgeQaService.FeedbackRequest;
import com.example.lexiflow.rag.service.KnowledgeQaService.QaAnswer;
import com.example.lexiflow.rag.service.KnowledgeQaService.QaQuestionRequest;
import com.example.lexiflow.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/knowledge-qa")
public class KnowledgeQaController {

    private final KnowledgeQaService knowledgeQaService;

    public KnowledgeQaController(KnowledgeQaService knowledgeQaService) {
        this.knowledgeQaService = knowledgeQaService;
    }

    @PostMapping("/ask")
    public ApiResponse<QaAnswer> ask(@RequestBody QaQuestionRequest request,
                                     @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeQaService.ask(request, user));
    }

    @GetMapping("/history")
    public ApiResponse<List<QaHistory>> history(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeQaService.history(user));
    }

    @PostMapping("/history/{id}/feedback")
    public ApiResponse<QaHistory> feedback(@PathVariable Long id,
                                           @RequestBody FeedbackRequest request,
                                           @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeQaService.feedback(id, request, user));
    }
}
