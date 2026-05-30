package com.example.lexiflow.rag.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.rag.model.DocumentChunk;
import com.example.lexiflow.rag.model.KnowledgeBase;
import com.example.lexiflow.rag.model.KnowledgeDocument;
import com.example.lexiflow.rag.service.KnowledgeBaseService;
import com.example.lexiflow.rag.service.RagRetrievalService;
import com.example.lexiflow.rag.service.RagRetrievalService.RetrievedChunk;
import com.example.lexiflow.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final RagRetrievalService retrievalService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, RagRetrievalService retrievalService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.retrievalService = retrievalService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody CreateKnowledgeBaseRequest request,
                                             @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeBaseService.createBase(request.name(), request.visibility(), user));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBase>> list(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeBaseService.listBases(user));
    }

    @PostMapping("/{id}/documents")
    public ApiResponse<KnowledgeDocument> uploadDocument(@PathVariable Long id,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(required = false) String title,
                                                         @RequestParam(required = false) String documentType,
                                                         @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeBaseService.uploadDocument(id, file, title, documentType, user));
    }

    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocument>> documents(@RequestParam(required = false) Long knowledgeBaseId,
                                                          @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeBaseService.listDocuments(knowledgeBaseId, user));
    }

    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<List<DocumentChunk>> chunks(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeBaseService.listChunks(id));
    }

    @PostMapping("/search")
    public ApiResponse<List<RetrievedChunk>> search(@Valid @RequestBody SearchRequest request,
                                                    @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(retrievalService.retrieve(request.reviewId(), request.query(), request.limit() == null ? 5 : request.limit(), user));
    }

    @PostMapping("/{id}/batch-import")
    public ApiResponse<java.util.Map<String, Object>> batchImport(@PathVariable Long id,
                                                                   @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(knowledgeBaseService.batchImportFromStorage(id, user));
    }

    public record CreateKnowledgeBaseRequest(@NotBlank String name, String visibility) {
    }

    public record SearchRequest(Long reviewId, @NotBlank String query, Integer limit) {
    }
}
