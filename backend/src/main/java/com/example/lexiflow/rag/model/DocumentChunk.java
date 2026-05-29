package com.example.lexiflow.rag.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.lexiflow.common.model.AuditFields;
import com.example.lexiflow.common.persistence.JsonbTypeHandler;
import org.apache.ibatis.type.JdbcType;

@TableName("document_chunk")
public class DocumentChunk extends AuditFields {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    @TableField(exist = false)
    private Object embedding;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbTypeHandler.class)
    private String metadata;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Object getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Object embedding) {
        this.embedding = embedding;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
