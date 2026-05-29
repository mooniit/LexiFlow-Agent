-- ============================================================
-- DB#19: pgvector 相似度检索函数与 IVFFlat 调优
-- DB#13: document_chunk 向量检索补充
-- ============================================================

-- 配置 IVFFlat 索引探测数（调优检索召回率）
-- 值越大召回越准但越慢；默认值为 1，建议设为 sqrt(总行数) 附近
-- 此处设置为 10，适合 MVP 规模
SET ivfflat.probes = 10;

-- 带权限过滤的相似度检索函数
CREATE OR REPLACE FUNCTION search_chunks_by_similarity(
    query_embedding VECTOR(1536),
    match_threshold FLOAT DEFAULT 0.7,
    match_count INT DEFAULT 10,
    p_knowledge_base_id BIGINT DEFAULT NULL,
    p_document_type VARCHAR DEFAULT NULL,
    p_allowed_roles JSONB DEFAULT NULL
)
RETURNS TABLE(
    chunk_id BIGINT,
    document_id BIGINT,
    document_title VARCHAR,
    content TEXT,
    chunk_index INT,
    similarity FLOAT,
    chunk_metadata JSONB
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        dc.id AS chunk_id,
        dc.document_id,
        kd.title AS document_title,
        dc.content,
        dc.chunk_index,
        (1 - (dc.embedding <=> query_embedding))::FLOAT AS similarity,
        dc.metadata AS chunk_metadata
    FROM document_chunk dc
    JOIN knowledge_document kd ON kd.id = dc.document_id
    JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
    WHERE dc.embedding IS NOT NULL
      AND kd.document_status = 'PUBLISHED'
      AND kb.status = 'ACTIVE'
      AND 1 - (dc.embedding <=> query_embedding) > match_threshold
      AND (p_knowledge_base_id IS NULL OR kb.id = p_knowledge_base_id)
      AND (p_document_type IS NULL OR kd.document_type = p_document_type)
      AND (p_allowed_roles IS NULL OR kb.allowed_roles && p_allowed_roles)
      AND dc.deleted = FALSE
      AND kd.deleted = FALSE
      AND kb.deleted = FALSE
    ORDER BY dc.embedding <=> query_embedding
    LIMIT match_count;
END $$;

COMMENT ON FUNCTION search_chunks_by_similarity IS '带权限过滤的向量相似度检索，支持按知识库、文档类型、角色过滤';
