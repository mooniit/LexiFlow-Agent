package com.example.lexiflow.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.config.StorageProperties;
import com.example.lexiflow.contract.service.ContractTextParser;
import com.example.lexiflow.llm.model.EmbeddingRequest;
import com.example.lexiflow.llm.model.EmbeddingResponse;
import com.example.lexiflow.llm.service.LlmGateway;
import com.example.lexiflow.rag.mapper.DocumentChunkMapper;
import com.example.lexiflow.rag.mapper.KnowledgeBaseMapper;
import com.example.lexiflow.rag.mapper.KnowledgeDocumentMapper;
import com.example.lexiflow.rag.model.DocumentChunk;
import com.example.lexiflow.rag.model.KnowledgeBase;
import com.example.lexiflow.rag.model.KnowledgeDocument;
import com.example.lexiflow.security.CurrentUser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final StorageProperties storageProperties;
    private final KnowledgeAccessGuard knowledgeAccessGuard;
    private final ContractTextParser contractTextParser;
    private final LlmGateway llmGateway;
    private final KnowledgeDocumentNormalizer documentNormalizer;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper, KnowledgeDocumentMapper documentMapper,
                                DocumentChunkMapper chunkMapper, StorageProperties storageProperties,
                                KnowledgeAccessGuard knowledgeAccessGuard, ContractTextParser contractTextParser,
                                LlmGateway llmGateway, KnowledgeDocumentNormalizer documentNormalizer) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.storageProperties = storageProperties;
        this.knowledgeAccessGuard = knowledgeAccessGuard;
        this.contractTextParser = contractTextParser;
        this.llmGateway = llmGateway;
        this.documentNormalizer = documentNormalizer;
    }

    @Transactional
    public KnowledgeBase createBase(String name, String visibility, CurrentUser user) {
        KnowledgeBase base = new KnowledgeBase();
        base.setName(name);
        base.setVisibility(StringUtils.hasText(visibility) ? visibility : "PRIVATE");
        base.setAllowedRoles("[]");
        base.setStatus("ACTIVE");
        base.setCreatedBy(user.id());
        base.setUpdatedBy(user.id());
        knowledgeBaseMapper.insert(base);
        return base;
    }

    public List<KnowledgeBase> listBases(CurrentUser user) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, false)
                .orderByDesc(KnowledgeBase::getCreatedAt))
                .stream()
                .filter(base -> knowledgeAccessGuard.canRead(base, user))
                .toList();
    }

    @Transactional
    public KnowledgeDocument uploadDocument(Long knowledgeBaseId, MultipartFile file, String title, String documentType,
                                            CurrentUser user) {
        KnowledgeBase base = requireBase(knowledgeBaseId);
        knowledgeAccessGuard.requireRead(base, user);
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "rule.txt" : file.getOriginalFilename());
        String fileType = resolveFileType(originalFilename);
        if (!List.of("txt", "md", "docx").contains(fileType)) {
            throw new IllegalArgumentException("Only txt, md, and docx knowledge documents are supported.");
        }
        try {
            Path targetDir = Path.of(storageProperties.knowledgeDir()).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(UUID.randomUUID() + "." + fileType).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            var parseResult = contractTextParser.parsePath(target, fileType);
            if (!parseResult.success()) {
                throw new IllegalStateException("Failed to parse document: " + parseResult.message());
            }
            KnowledgeDocument document = new KnowledgeDocument();
            document.setKnowledgeBaseId(base.getId());
            document.setTitle(StringUtils.hasText(title) ? title : originalFilename);
            document.setDocumentType(StringUtils.hasText(documentType) ? documentType : "RULE");
            document.setDocumentStatus("ACTIVE");
            document.setFilePath(target.toString());
            String normalizedText = documentNormalizer.normalize(parseResult.text(), fileType);
            document.setMetadata("{\"originalFilename\":" + JsonStrings.quote(originalFilename)
                    + ",\"fileType\":" + JsonStrings.quote(fileType)
                    + ",\"normalization\":\"legal-article-v1\"}");
            document.setCreatedBy(user.id());
            document.setUpdatedBy(user.id());
            documentMapper.insert(document);
            ingestChunks(document, normalizedText, user.id());
            return document;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store knowledge document.", ex);
        }
    }

    public List<KnowledgeDocument> listDocuments(Long knowledgeBaseId, CurrentUser user) {
        LambdaQueryWrapper<KnowledgeDocument> query = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDeleted, false)
                .eq(KnowledgeDocument::getDocumentStatus, "ACTIVE")
                .orderByDesc(KnowledgeDocument::getCreatedAt);
        if (knowledgeBaseId != null) {
            KnowledgeBase base = requireBase(knowledgeBaseId);
            knowledgeAccessGuard.requireRead(base, user);
            query.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
        }
        return documentMapper.selectList(query).stream()
                .filter(document -> knowledgeAccessGuard.canRead(requireBase(document.getKnowledgeBaseId()), user))
                .toList();
    }

    public List<DocumentChunk> listChunks(Long documentId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .eq(DocumentChunk::getDeleted, false)
                .orderByAsc(DocumentChunk::getChunkIndex));
    }

    @Transactional
    public Map<String, Object> batchImportFromStorage(Long knowledgeBaseId, CurrentUser user) {
        KnowledgeBase base = requireBase(knowledgeBaseId);
        knowledgeAccessGuard.requireRead(base, user);
        Path storageDir = Path.of(storageProperties.knowledgeDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(storageDir)) {
            return Map.of("imported", 0, "skipped", 0, "errors", List.of("Knowledge storage directory not found: " + storageDir));
        }
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        try (Stream<Path> files = Files.list(storageDir)) {
            List<Path> candidates = files
                    .filter(f -> {
                        String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".md") || name.endsWith(".docx") || name.endsWith(".txt");
                    })
                    .sorted()
                    .toList();
            for (Path filePath : candidates) {
                String filename = filePath.getFileName().toString();
                String fileType = resolveFileType(filename);
                String title = filename.substring(0, filename.lastIndexOf('.'));
                boolean exists = documentMapper.exists(new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(KnowledgeDocument::getTitle, title)
                        .eq(KnowledgeDocument::getDeleted, false));
                if (exists) {
                    skipped++;
                    continue;
                }
                try {
                    var parseResult = contractTextParser.parsePath(filePath, fileType);
                    if (!parseResult.success()) {
                        errors.add(filename + ": " + parseResult.message());
                        continue;
                    }
                    KnowledgeDocument document = new KnowledgeDocument();
                    document.setKnowledgeBaseId(base.getId());
                    document.setTitle(title);
                    document.setDocumentType("RULE");
                    document.setDocumentStatus("ACTIVE");
                    document.setFilePath(filePath.toString());
                    String normalizedText = documentNormalizer.normalize(parseResult.text(), fileType);
                    document.setMetadata("{\"originalFilename\":" + JsonStrings.quote(filename)
                            + ",\"fileType\":" + JsonStrings.quote(fileType)
                            + ",\"source\":\"batch-import\",\"normalization\":\"legal-article-v1\"}");
                    document.setCreatedBy(user.id());
                    document.setUpdatedBy(user.id());
                    documentMapper.insert(document);
                    ingestChunks(document, normalizedText, user.id());
                    imported++;
                } catch (Exception ex) {
                    String detail = ex.getMessage();
                    if (ex.getCause() != null) {
                        detail = detail + " | cause: " + ex.getCause().getMessage();
                    }
                    errors.add(filename + ": " + detail);
                }
            }
        } catch (IOException ex) {
            errors.add("Failed to list storage directory: " + ex.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    public KnowledgeBase requireBase(Long id) {
        KnowledgeBase base = knowledgeBaseMapper.selectById(id);
        if (base == null || Boolean.TRUE.equals(base.getDeleted())) {
            throw new IllegalArgumentException("Knowledge base not found: " + id);
        }
        return base;
    }

    private void ingestChunks(KnowledgeDocument document, String content, Long userId) {
        if (!StringUtils.hasText(content)) {
            DocumentChunk chunk = buildChunk(document.getId(), 0, "Empty document content.", userId, null, "{}");
            chunkMapper.insert(chunk);
            return;
        }
        List<ArticleChunk> articleChunks = splitByArticlesWithMeta(content, MAX_CHUNK_CHARS);
        List<String> textsToEmbed = new ArrayList<>();
        List<DocumentChunk> chunkRecords = new ArrayList<>();
        for (int i = 0; i < articleChunks.size(); i++) {
            ArticleChunk ac = articleChunks.get(i);
            DocumentChunk chunk = buildChunk(document.getId(), i, ac.content(), userId, null, ac.articleRef());
            chunkRecords.add(chunk);
            textsToEmbed.add(ac.content());
        }
        List<List<Double>> embeddings = batchEmbed(textsToEmbed, document.getId());
        for (int i = 0; i < chunkRecords.size(); i++) {
            DocumentChunk chunk = chunkRecords.get(i);
            chunk.setEmbedding(vectorToString(embeddings.get(i)));
            chunkMapper.insert(chunk);
        }
    }

    private static final int EMBEDDING_BATCH_SIZE = 10;
    private static final int MAX_CHUNK_CHARS = 450;
    private static final int CHUNK_OVERLAP_CHARS = 60;

    private List<List<Double>> batchEmbed(List<String> texts, Long documentId) {
        List<List<Double>> allEmbeddings = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(start, end);
            try {
                EmbeddingResponse response = llmGateway.embed(new EmbeddingRequest(null, batch));
                allEmbeddings.addAll(response.embeddings());
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Embedding failed for document " + documentId + " batch [" + start + "-" + end + "]: " + ex.getMessage(), ex);
            }
        }
        return allEmbeddings;
    }

    private DocumentChunk buildChunk(Long documentId, int index, String content, Long userId, String embedding, String metadata) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setMetadata(metadata != null ? metadata : "{}");
        chunk.setEmbedding(embedding);
        chunk.setCreatedBy(userId);
        chunk.setUpdatedBy(userId);
        return chunk;
    }

    record ArticleChunk(String content, String articleRef) {}

    private String extractArticleRef(String block) {
        if (block == null || block.isBlank()) {
            return null;
        }
        int end = block.indexOf('\n');
        String firstLine = end > 0 ? block.substring(0, end) : block.substring(0, Math.min(block.length(), 60));
        int tiaoIdx = firstLine.indexOf("条");
        if (tiaoIdx > 0) {
            int start = Math.max(0, tiaoIdx - 12);
            return firstLine.substring(start, tiaoIdx + 1).trim();
        }
        return null;
    }

    private String buildArticleMeta(List<String> articleRefs) {
        if (articleRefs == null || articleRefs.isEmpty()) {
            return "{}";
        }
        String joined = articleRefs.stream()
                .map(JsonStrings::quote)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return "{\"articles\":[" + joined + "]}";
    }

    List<ArticleChunk> splitByArticlesWithMeta(String content, int maxLength) {
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> blocks = splitByArticleBoundary(normalized);
        List<ArticleChunk> chunks = new ArrayList<>();
        for (String block : blocks) {
            String articleRef = extractArticleRef(block);
            String metadata = buildArticleMeta(articleRef == null ? List.of() : List.of(articleRef));
            if (block.length() <= maxLength) {
                chunks.add(new ArticleChunk(block.trim(), metadata));
            } else {
                chunks.addAll(splitLongArticle(block, maxLength, metadata));
            }
        }
        return chunks.isEmpty() ? List.of(new ArticleChunk(normalized, "{}")) : chunks;
    }

    private List<String> splitByArticleBoundary(String text) {
        List<String> blocks = new ArrayList<>();
        String[] parts = text.split("(?m)(?=^\\s*(?:第\\s*[一二三四五六七八九十百千万零〇两0-9]+\\s*条|Article\\s+\\d+)(?:\\s|$|[：:、.．]))");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(trimmed);
            }
        }
        return blocks.isEmpty() ? List.of(text) : blocks;
    }

    private List<ArticleChunk> splitLongArticle(String block, int maxLength, String metadata) {
        List<ArticleChunk> chunks = new ArrayList<>();
        String remaining = block.trim();
        while (remaining.length() > maxLength) {
            int splitPoint = findSafeSplit(remaining, maxLength);
            String piece = remaining.substring(0, splitPoint).trim();
            if (!piece.isEmpty()) {
                chunks.add(new ArticleChunk(piece, metadata));
            }
            int nextStart = Math.max(0, splitPoint - CHUNK_OVERLAP_CHARS);
            remaining = remaining.substring(nextStart).trim();
            if (remaining.length() <= maxLength) {
                break;
            }
        }
        if (!remaining.isEmpty()) {
            chunks.add(new ArticleChunk(remaining, metadata));
        }
        return chunks;
    }

    private int findSafeSplit(String text, int targetLength) {
        int candidate = text.lastIndexOf('\n', targetLength);
        if (candidate > targetLength / 2) {
            return candidate + 1;
        }
        candidate = text.lastIndexOf('。', targetLength);
        if (candidate > targetLength / 2) {
            return candidate + 1;
        }
        return targetLength;
    }

    private String vectorToString(List<Double> vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private String resolveFileType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
