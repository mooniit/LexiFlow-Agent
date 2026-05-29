-- ============================================================
-- DB#14: Prompt 模板表
-- ============================================================

CREATE TABLE prompt_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    version VARCHAR(32) NOT NULL,
    scene VARCHAR(64) NOT NULL,
    description TEXT,
    template_content TEXT NOT NULL,
    variables JSONB NOT NULL DEFAULT '[]'::jsonb,
    output_constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE prompt_template IS 'Prompt 模板与版本管理';
COMMENT ON COLUMN prompt_template.scene IS '适用场景：CLAUSE_EXTRACTION/RISK_ANALYSIS/RULE_EXPLANATION/SUGGESTION_GENERATION/REPORT_GENERATION/KNOWLEDGE_QA';
COMMENT ON COLUMN prompt_template.variables IS '模板变量定义，如 [{"name":"contract_text","required":true},{"name":"clause_name","required":false}]';
COMMENT ON COLUMN prompt_template.output_constraints IS '输出约束，如 {"format":"json","max_tokens":2000}';
COMMENT ON COLUMN prompt_template.template_content IS 'Prompt 模板正文，使用 {{variable}} 占位符';

CREATE INDEX IF NOT EXISTS idx_prompt_template_scene ON prompt_template(scene);
CREATE INDEX IF NOT EXISTS idx_prompt_template_enabled ON prompt_template(enabled);
CREATE UNIQUE INDEX IF NOT EXISTS idx_prompt_template_name_version ON prompt_template(name, version) WHERE deleted = FALSE;
