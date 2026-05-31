package com.example.lexiflow.rag.service;

import com.example.lexiflow.llm.model.EmbeddingResponse;
import com.example.lexiflow.llm.model.TokenUsage;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.llm.service.LlmGatewayException;
import com.example.lexiflow.rag.mapper.DocumentChunkMapper;
import com.example.lexiflow.rag.mapper.KnowledgeBaseMapper;
import com.example.lexiflow.rag.mapper.KnowledgeDocumentMapper;
import com.example.lexiflow.rag.mapper.RetrievalLogMapper;
import com.example.lexiflow.rag.model.KnowledgeBase;
import com.example.lexiflow.rag.model.KnowledgeDocument;
import com.example.lexiflow.rag.model.RetrievalLog;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.tool.service.ToolPermissionGuard;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkRetrievalTest {

    @Mock
    private DocumentChunkMapper chunkMapper;
    @Mock
    private KnowledgeDocumentMapper documentMapper;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private RetrievalLogMapper retrievalLogMapper;
    @Mock
    private KnowledgeAccessGuard knowledgeAccessGuard;
    @Mock
    private ToolPermissionGuard toolPermissionGuard;
    @Mock
    private LlmGateway llmGateway;

    private RagRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new RagRetrievalService(chunkMapper, documentMapper, knowledgeBaseMapper,
                retrievalLogMapper, knowledgeAccessGuard, toolPermissionGuard, llmGateway, 0.40);
    }

    // === retrieval main path ===

    @Test
    void returnsRankedChunksByVectorSimilarity() {
        KnowledgeBase base = activeBase();
        KnowledgeDocument document = activeDocument(1L);
        CurrentUser user = adminUser();
        List<Double> mockVector = vectorOf(1536, 0.02);
        List<DocumentChunkMapper.VectorSearchRow> rows = List.of(
                row(10L, 1L, "电子签名法", "第十三条 可靠电子签名须满足...", 3, 0.72, "{}"),
                row(11L, 1L, "电子签名法", "第十四条 可靠的电子签名与手写签名...", 4, 0.65, "{}")
        );

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(base));
        when(knowledgeAccessGuard.canRead(any(KnowledgeBase.class), any())).thenReturn(true);
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(llmGateway.embed(any())).thenReturn(new EmbeddingResponse(List.of(mockVector), new TokenUsage(5, 0), "dashscope", "text-embedding-v4"));
        when(chunkMapper.searchByVector(anyString(), eq(0.40), eq(6), eq(1L), isNull(), eq("[\"ADMIN\"]")))
                .thenReturn(rows);
        when(retrievalLogMapper.insert(any(RetrievalLog.class))).thenReturn(1);

        List<RagRetrievalService.RetrievedChunk> results = service.retrieve(1L, "可靠电子签名条件", 2, user);

        Assertions.assertThat(results).hasSize(2);
        Assertions.assertThat(results.get(0).score()).isEqualTo(0.72);
        Assertions.assertThat(results.get(1).score()).isEqualTo(0.65);
        Assertions.assertThat(results.get(0).content()).contains("第十三条", "电子签名法");
    }

    @Test
    void filtersOutChunksFromInaccessibleDocuments() {
        KnowledgeBase base = activeBase();
        KnowledgeDocument document = activeDocument(1L);
        CurrentUser user = adminUser();
        List<Double> mockVector = vectorOf(1536, 0.02);
        List<DocumentChunkMapper.VectorSearchRow> rows = List.of(
                row(10L, 1L, "电子签名法", "第十三条 ...", 0, 0.72, "{}"),
                row(20L, 99L, "机密文件", "不应该出现的内容", 0, 0.68, "{}")
        );

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(base));
        when(knowledgeAccessGuard.canRead(any(KnowledgeBase.class), any())).thenReturn(true);
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(llmGateway.embed(any())).thenReturn(new EmbeddingResponse(List.of(mockVector), new TokenUsage(5, 0), "dashscope", "text-embedding-v4"));
        when(chunkMapper.searchByVector(anyString(), anyDouble(), anyInt(), anyLong(), isNull(), anyString()))
                .thenReturn(rows);
        when(retrievalLogMapper.insert(any(RetrievalLog.class))).thenReturn(1);

        List<RagRetrievalService.RetrievedChunk> results = service.retrieve(1L, "可靠电子签名条件", 3, user);

        Assertions.assertThat(results).hasSize(1);
        Assertions.assertThat(results.get(0).documentId()).isEqualTo(1L);
    }

    @Test
    void returnsEmptyWhenNoAccessibleDocuments() {
        KnowledgeBase base = activeBase();
        CurrentUser user = adminUser();

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(base));
        when(knowledgeAccessGuard.canRead(any(KnowledgeBase.class), any())).thenReturn(false);

        List<RagRetrievalService.RetrievedChunk> results = service.retrieve(1L, "test query", 5, user);

        Assertions.assertThat(results).isEmpty();
    }

    @Test
    void propagatesEmbeddingFailure() {
        KnowledgeBase base = activeBase();
        KnowledgeDocument document = activeDocument(1L);
        CurrentUser user = adminUser();

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(base));
        when(knowledgeAccessGuard.canRead(any(KnowledgeBase.class), any())).thenReturn(true);
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(llmGateway.embed(any())).thenThrow(new LlmGatewayException("Embedding provider unavailable"));

        Assertions.assertThatThrownBy(() -> service.retrieve(1L, "test", 5, user))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("Embedding provider unavailable");
    }

    // === result structure ===

    @Test
    void resultContainsDocumentTitleInContent() {
        KnowledgeBase base = activeBase();
        KnowledgeDocument document = activeDocument(1L);
        CurrentUser user = adminUser();
        List<Double> mockVector = vectorOf(1536, 0.02);
        List<DocumentChunkMapper.VectorSearchRow> rows = List.of(
                row(10L, 1L, "中华人民共和国劳动合同法", "第三十九条 劳动者有下列情形之一的...", 0, 0.85, "{\"articles\":[\"第三十九条\"]}")
        );

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(base));
        when(knowledgeAccessGuard.canRead(any(KnowledgeBase.class), any())).thenReturn(true);
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(llmGateway.embed(any())).thenReturn(new EmbeddingResponse(List.of(mockVector), new TokenUsage(5, 0), "dashscope", "text-embedding-v4"));
        when(chunkMapper.searchByVector(anyString(), anyDouble(), anyInt(), anyLong(), isNull(), anyString()))
                .thenReturn(rows);
        when(retrievalLogMapper.insert(any(RetrievalLog.class))).thenReturn(1);

        List<RagRetrievalService.RetrievedChunk> results = service.retrieve(1L, "劳动合同解除", 3, user);

        Assertions.assertThat(results.get(0).content())
                .contains("中华人民共和国劳动合同法")
                .contains("第三十九条");
    }

    @Test
    void scoreIsTruncatedToFourDecimalPlaces() {
        KnowledgeBase base = activeBase();
        KnowledgeDocument document = activeDocument(1L);
        CurrentUser user = adminUser();
        List<Double> mockVector = vectorOf(1536, 0.02);
        List<DocumentChunkMapper.VectorSearchRow> rows = List.of(
                row(10L, 1L, "法", "条", 0, 0.123456789, "{}")
        );

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(base));
        when(knowledgeAccessGuard.canRead(any(KnowledgeBase.class), any())).thenReturn(true);
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(llmGateway.embed(any())).thenReturn(new EmbeddingResponse(List.of(mockVector), new TokenUsage(5, 0), "dashscope", "text-embedding-v4"));
        when(chunkMapper.searchByVector(anyString(), anyDouble(), anyInt(), anyLong(), isNull(), anyString()))
                .thenReturn(rows);
        when(retrievalLogMapper.insert(any(RetrievalLog.class))).thenReturn(1);

        List<RagRetrievalService.RetrievedChunk> results = service.retrieve(1L, "test", 1, user);

        Assertions.assertThat(results.get(0).score()).isEqualTo(0.1235);
    }

    // === helpers ===

    private KnowledgeBase activeBase() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(1L);
        base.setName("合规库");
        base.setVisibility("PUBLIC");
        base.setAllowedRoles("[]");
        base.setStatus("ACTIVE");
        base.setCreatedBy(1L);
        return base;
    }

    private KnowledgeDocument activeDocument(Long id) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setKnowledgeBaseId(1L);
        document.setTitle("doc");
        document.setDocumentStatus("ACTIVE");
        document.setDeleted(false);
        return document;
    }

    private CurrentUser adminUser() {
        return new CurrentUser(1L, "admin", "Admin", null, List.of("ADMIN"), List.of("knowledge:read", "trace:read"), true);
    }

    private DocumentChunkMapper.VectorSearchRow row(Long chunkId, Long documentId, String title, String content,
                                                      int chunkIndex, double similarity, String metadata) {
        return new DocumentChunkMapper.VectorSearchRow(chunkId, documentId, title, content, chunkIndex, similarity, metadata);
    }

    private List<Double> vectorOf(int dims, double value) {
        Double[] arr = new Double[dims];
        for (int i = 0; i < dims; i++) {
            arr[i] = value;
        }
        return List.of(arr);
    }
}
