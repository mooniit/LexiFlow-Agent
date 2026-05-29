package com.example.lexiflow.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.rag.mapper.DocumentChunkMapper;
import com.example.lexiflow.rag.mapper.RetrievalLogMapper;
import com.example.lexiflow.rag.model.DocumentChunk;
import com.example.lexiflow.rag.model.RetrievalLog;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {

    private final DocumentChunkMapper chunkMapper;
    private final RetrievalLogMapper retrievalLogMapper;

    public RagRetrievalService(DocumentChunkMapper chunkMapper, RetrievalLogMapper retrievalLogMapper) {
        this.chunkMapper = chunkMapper;
        this.retrievalLogMapper = retrievalLogMapper;
    }

    public List<RetrievedChunk> retrieve(Long reviewId, String query, int limit, Long userId) {
        Instant start = Instant.now();
        List<DocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDeleted, false));
        List<RetrievedChunk> results = chunks.stream()
                .map(chunk -> new RetrievedChunk(chunk.getId(), chunk.getDocumentId(), chunk.getContent(), score(query, chunk.getContent())))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
        log(reviewId, query, results, Duration.between(start, Instant.now()).toMillis(), userId);
        return results;
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

    private void log(Long reviewId, String query, List<RetrievedChunk> results, long latencyMs, Long userId) {
        RetrievalLog log = new RetrievalLog();
        log.setReviewId(reviewId);
        log.setQueryText(query);
        log.setFilterConditions("{}");
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
}
