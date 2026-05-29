package com.example.lexiflow.rag.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.lexiflow.common.persistence.JsonbTypeHandler;
import java.time.OffsetDateTime;
import org.apache.ibatis.type.JdbcType;

@TableName("retrieval_log")
public class RetrievalLog {

    private Long id;
    private Long reviewId;
    private String queryText;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbTypeHandler.class)
    private String filterConditions;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbTypeHandler.class)
    private String retrievedChunks;
    private Long latencyMs;
    private Long createdBy;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getFilterConditions() {
        return filterConditions;
    }

    public void setFilterConditions(String filterConditions) {
        this.filterConditions = filterConditions;
    }

    public String getRetrievedChunks() {
        return retrievedChunks;
    }

    public void setRetrievedChunks(String retrievedChunks) {
        this.retrievedChunks = retrievedChunks;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
