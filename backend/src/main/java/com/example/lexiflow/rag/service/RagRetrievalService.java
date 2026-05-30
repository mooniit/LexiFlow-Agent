package com.example.lexiflow.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.llm.model.EmbeddingRequest;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.rag.mapper.DocumentChunkMapper;
import com.example.lexiflow.rag.mapper.DocumentChunkMapper.VectorSearchRow;
import com.example.lexiflow.rag.mapper.KnowledgeBaseMapper;
import com.example.lexiflow.rag.mapper.KnowledgeDocumentMapper;
import com.example.lexiflow.rag.mapper.RetrievalLogMapper;
import com.example.lexiflow.rag.model.KnowledgeBase;
import com.example.lexiflow.rag.model.KnowledgeDocument;
import com.example.lexiflow.rag.model.RetrievalLog;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {

    private static final double SIMILARITY_THRESHOLD = 0.55;

    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RetrievalLogMapper retrievalLogMapper;
    private final KnowledgeAccessGuard knowledgeAccessGuard;
    private final ToolPermissionGuard toolPermissionGuard;
    private final LlmGateway llmGateway;

    public RagRetrievalService(DocumentChunkMapper chunkMapper, KnowledgeDocumentMapper documentMapper,
                               KnowledgeBaseMapper knowledgeBaseMapper, RetrievalLogMapper retrievalLogMapper,
                               KnowledgeAccessGuard knowledgeAccessGuard, ToolPermissionGuard toolPermissionGuard,
                               LlmGateway llmGateway) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.retrievalLogMapper = retrievalLogMapper;
        this.knowledgeAccessGuard = knowledgeAccessGuard;
        this.toolPermissionGuard = toolPermissionGuard;
        this.llmGateway = llmGateway;
    }

    public List<RetrievedChunk> retrieve(Long reviewId, String query, int limit, CurrentUser user) {
        toolPermissionGuard.requireAllowed("rule_retrieval", user);
        Instant start = Instant.now();

        AccessScope scope = accessScope(user);
        if (scope.documentIds().isEmpty()) {
            log(reviewId, query, scope, List.of(), Duration.between(start, Instant.now()).toMillis(), user.id());
            return List.of();
        }

        String queryEmbedding = generateQueryEmbedding(query);
        if (queryEmbedding == null) {
            log(reviewId, query, scope, List.of(), Duration.between(start, Instant.now()).toMillis(), user.id());
            return List.of();
        }

        String allowedRolesJson = user.roles() != null && !user.roles().isEmpty()
                ? "[\"" + String.join("\",\"", user.roles()) + "\"]"
                : "[]";

        List<RetrievedChunk> results = chunkMapper.searchByVector(
                        queryEmbedding,
                        SIMILARITY_THRESHOLD,
                        limit * 3,
                        scope.knowledgeBaseIds().isEmpty() ? null : scope.knowledgeBaseIds().iterator().next(),
                        null,
                        allowedRolesJson)
                .stream()
                .filter(row -> scope.documentIds().contains(row.documentId()))
                .map(row -> new RetrievedChunk(row.chunkId(), row.documentId(),
                        row.documentTitle() + "\n" + row.content(),
                        truncate(row.similarity())))
                .toList();

        log(reviewId, query, scope, results, Duration.between(start, Instant.now()).toMillis(), user.id());
        return results;
    }

    private String generateQueryEmbedding(String query) {
        try {
            var response = llmGateway.embed(new EmbeddingRequest(null, List.of(query)));
            if (response.embeddings() == null || response.embeddings().isEmpty()) {
                return null;
            }
            List<Double> vector = response.embeddings().get(0);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vector.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(vector.get(i));
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private AccessScope accessScope(CurrentUser user) {
        List<KnowledgeBase> bases = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, false)
                .eq(KnowledgeBase::getStatus, "ACTIVE"));
        Set<Long> baseIds = bases.stream()
                .filter(base -> knowledgeAccessGuard.canRead(base, user))
                .map(KnowledgeBase::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (baseIds.isEmpty()) {
            return new AccessScope(Set.of(), Set.of());
        }
        List<KnowledgeDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDeleted, false)
                .eq(KnowledgeDocument::getDocumentStatus, "ACTIVE")
                .in(KnowledgeDocument::getKnowledgeBaseId, baseIds));
        Set<Long> documentIds = documents.stream()
                .map(KnowledgeDocument::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new AccessScope(baseIds, documentIds);
    }

    private void log(Long reviewId, String query, AccessScope scope, List<RetrievedChunk> results, long latencyMs, Long userId) {
        RetrievalLog log = new RetrievalLog();
        log.setReviewId(reviewId);
        log.setQueryText(query);
        log.setFilterConditions("{\"knowledgeBaseIds\":" + JsonStrings.quote(scope.knowledgeBaseIds().toString())
                + ",\"documentIds\":" + JsonStrings.quote(scope.documentIds().toString()) + "}");
        log.setRetrievedChunks(toJson(results));
        log.setLatencyMs(latencyMs);
        log.setCreatedBy(userId);
        retrievalLogMapper.insert(log);
    }

    private String toJson(List<RetrievedChunk> chunks) {
        return "[" + chunks.stream()
                .map(chunk -> "{\"chunkId\":" + chunk.chunkId()
                        + ",\"documentId\":" + chunk.documentId()
                        + ",\"score\":" + String.format(Locale.ROOT, "%.4f", chunk.score())
                        + ",\"content\":" + JsonStrings.quote(chunk.content()) + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private double truncate(Double value) {
        if (value == null) return 0;
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record RetrievedChunk(Long chunkId, Long documentId, String content, double score) {
    }

    private record AccessScope(Set<Long> knowledgeBaseIds, Set<Long> documentIds) {
    }
}
