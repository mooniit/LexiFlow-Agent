package com.example.lexiflow.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.common.util.JsonStrings;
import com.example.lexiflow.config.StorageProperties;
import com.example.lexiflow.contract.service.ContractTextParser;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper, KnowledgeDocumentMapper documentMapper,
                                DocumentChunkMapper chunkMapper, StorageProperties storageProperties,
                                KnowledgeAccessGuard knowledgeAccessGuard) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.storageProperties = storageProperties;
        this.knowledgeAccessGuard = knowledgeAccessGuard;
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
        if (!List.of("txt", "docx").contains(fileType)) {
            throw new IllegalArgumentException("Only txt and docx knowledge documents are supported.");
        }
        try {
            Path targetDir = Path.of(storageProperties.knowledgeDir()).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(UUID.randomUUID() + "." + fileType).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String content = "txt".equals(fileType)
                    ? Files.readString(target)
                    : "";
            KnowledgeDocument document = new KnowledgeDocument();
            document.setKnowledgeBaseId(base.getId());
            document.setTitle(StringUtils.hasText(title) ? title : originalFilename);
            document.setDocumentType(StringUtils.hasText(documentType) ? documentType : "RULE");
            document.setDocumentStatus("ACTIVE");
            document.setFilePath(target.toString());
            document.setMetadata("{\"originalFilename\":" + JsonStrings.quote(originalFilename) + ",\"fileType\":" + JsonStrings.quote(fileType) + "}");
            document.setCreatedBy(user.id());
            document.setUpdatedBy(user.id());
            documentMapper.insert(document);
            ingestChunks(document, content, user.id());
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

    public KnowledgeBase requireBase(Long id) {
        KnowledgeBase base = knowledgeBaseMapper.selectById(id);
        if (base == null || Boolean.TRUE.equals(base.getDeleted())) {
            throw new IllegalArgumentException("Knowledge base not found: " + id);
        }
        return base;
    }

    private void ingestChunks(KnowledgeDocument document, String content, Long userId) {
        if (!StringUtils.hasText(content)) {
            DocumentChunk chunk = buildChunk(document.getId(), 0, "DOCX parsing is not enabled yet. Upload txt rules for searchable content.", userId);
            chunkMapper.insert(chunk);
            return;
        }
        List<String> chunks = split(content, 800);
        for (int i = 0; i < chunks.size(); i++) {
            chunkMapper.insert(buildChunk(document.getId(), i, chunks.get(i), userId));
        }
    }

    private DocumentChunk buildChunk(Long documentId, int index, String content, Long userId) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setMetadata("{}");
        chunk.setCreatedBy(userId);
        chunk.setUpdatedBy(userId);
        return chunk;
    }

    private List<String> split(String content, int maxLength) {
        List<String> chunks = new ArrayList<>();
        String normalized = content.replace("\r\n", "\n").trim();
        for (int start = 0; start < normalized.length(); start += maxLength) {
            chunks.add(normalized.substring(start, Math.min(normalized.length(), start + maxLength)).trim());
        }
        return chunks.isEmpty() ? List.of(normalized) : chunks;
    }

    private String resolveFileType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
