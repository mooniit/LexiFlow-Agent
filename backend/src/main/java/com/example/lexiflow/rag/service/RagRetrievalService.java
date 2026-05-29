package com.example.lexiflow.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.rag.mapper.DocumentChunkMapper;
import com.example.lexiflow.rag.mapper.KnowledgeBaseMapper;
import com.example.lexiflow.rag.mapper.KnowledgeDocumentMapper;
import com.example.lexiflow.rag.mapper.RetrievalLogMapper;
import com.example.lexiflow.rag.model.DocumentChunk;
import com.example.lexiflow.rag.model.KnowledgeBase;
import com.example.lexiflow.rag.model.KnowledgeDocument;
import com.example.lexiflow.rag.model.RetrievalLog;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {

    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RetrievalLogMapper retrievalLogMapper;
    private final KnowledgeAccessGuard knowledgeAccessGuard;
    private final ToolPermissionGuard toolPermissionGuard;

    public RagRetrievalService(DocumentChunkMapper chunkMapper, KnowledgeDocumentMapper documentMapper,
                               KnowledgeBaseMapper knowledgeBaseMapper, RetrievalLogMapper retrievalLogMapper,
                               KnowledgeAccessGuard knowledgeAccessGuard, ToolPermissionGuard toolPermissionGuard) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.retrievalLogMapper = retrievalLogMapper;
        this.knowledgeAccessGuard = knowledgeAccessGuard;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    public List<RetrievedChunk> retrieve(Long reviewId, String query, int limit, CurrentUser user) {
        toolPermissionGuard.requireAllowed("rule_retrieval", user);
        Instant start = Instant.now();
        AccessScope scope = accessScope(user);
        if (scope.documentIds().isEmpty()) {
            log(reviewId, query, scope, List.of(), Duration.between(start, Instant.now()).toMillis(), user.id());
            return List.of();
        }
        List<DocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDeleted, false)
                .in(DocumentChunk::getDocumentId, scope.documentIds()));
        List<RetrievedChunk> results = chunks.stream()
                .map(chunk -> new RetrievedChunk(chunk.getId(), chunk.getDocumentId(), chunk.getContent(), score(query, chunk.getContent())))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
        log(reviewId, query, scope, results, Duration.between(start, Instant.now()).toMillis(), user.id());
        return results;
    }

    private AccessScope accessScope(CurrentUser user) {
        List<KnowledgeBase> bases = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, false)
                .eq(KnowledgeBase::getStatus, "ACTIVE"));
        Set<Long> baseIds = bases.stream()
                .filter(base -> knowledgeAccessGuard.canRead(base, user))
                .map(KnowledgeBase::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new AccessScope(baseIds, documentIds);
    }

    private double score(String query, String content) {
        Set<String> queryTokens = tokens(query);
        Set<String> contentTokens = tokens(content);
        if (queryTokens.isEmpty() || contentTokens.isEmpty()) {
            return 0;
        }
        long hits = queryTokens.stream().filter(contentTokens::contains).count();
        return hits / (double) queryTokens.size();
    }

    private Set<String> tokens(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]+", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : normalized.split("\\s+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            char ch = normalized.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                tokens.add(normalized.substring(i, Math.min(normalized.length(), i + 2)));
            }
        }
        return tokens;
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

    public record RetrievedChunk(Long chunkId, Long documentId, String content, double score) {
    }

    private record AccessScope(Set<Long> knowledgeBaseIds, Set<Long> documentIds) {
    }
}
