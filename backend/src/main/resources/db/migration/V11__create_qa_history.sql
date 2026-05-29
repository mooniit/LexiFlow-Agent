-- ============================================================
-- DB#16: 合规知识库问答历史表
-- ============================================================

CREATE TABLE qa_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(id),
    knowledge_base_id BIGINT REFERENCES knowledge_base(id),
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    references_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    retrieved_chunks JSONB NOT NULL DEFAULT '[]'::jsonb,
    feedback VARCHAR(16),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE qa_history IS '合规知识库问答历史';
COMMENT ON COLUMN qa_history.references_json IS '引用的文档和 chunk 来源，含文档标题和相似度分数';
COMMENT ON COLUMN qa_history.retrieved_chunks IS 'RAG 检索到的相关 chunk 内容';
COMMENT ON COLUMN qa_history.feedback IS '用户反馈：NULL/HELPFUL/NOT_HELPFUL';

CREATE INDEX IF NOT EXISTS idx_qa_history_user_id ON qa_history(user_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_kb_id ON qa_history(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_created_at ON qa_history(created_at);
