-- ============================================================
-- DB#15: 审查工具配置表
-- ============================================================

CREATE TABLE review_tool (
    id BIGSERIAL PRIMARY KEY,
    tool_name VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    required_permission VARCHAR(128),
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    parameters_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    department_scope JSONB NOT NULL DEFAULT '[]'::jsonb,
    rate_limit JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE review_tool IS '审查工具注册与配置';
COMMENT ON COLUMN review_tool.required_permission IS '调用工具所需权限码，如 tool:execute、admin:manage';
COMMENT ON COLUMN review_tool.risk_level IS '工具自身风险等级：LOW/MEDIUM/HIGH';
COMMENT ON COLUMN review_tool.parameters_schema IS 'JSON Schema 定义的工具参数规范';
COMMENT ON COLUMN review_tool.department_scope IS '允许调用的部门范围，空数组代表全局可用';
COMMENT ON COLUMN review_tool.rate_limit IS '限流配置，如 {"per_minute":10,"per_hour":100}';

CREATE INDEX IF NOT EXISTS idx_review_tool_risk_level ON review_tool(risk_level);
CREATE INDEX IF NOT EXISTS idx_review_tool_enabled ON review_tool(enabled);
CREATE INDEX IF NOT EXISTS idx_review_tool_approval_required ON review_tool(approval_required);

-- 初始化三个内置审查工具
INSERT INTO review_tool (tool_name, display_name, description, required_permission, risk_level, approval_required, parameters_schema) VALUES
('contract_parser', '合同文本解析器', '读取合同文件并提取纯文本内容', 'tool:execute', 'LOW', FALSE,
 '{"type":"object","properties":{"contract_id":{"type":"integer"}},"required":["contract_id"]}'),
('clause_extractor', '条款抽取器', '从合同文本抽取结构化条款', 'tool:execute', 'LOW', FALSE,
 '{"type":"object","properties":{"contract_id":{"type":"integer"},"clause_types":{"type":"array"}},"required":["contract_id"]}'),
('risk_analyzer', '条款风险分析器', '基于合规规则对条款进行风险识别', 'tool:execute', 'MEDIUM', FALSE,
 '{"type":"object","properties":{"review_id":{"type":"integer"},"clause_ids":{"type":"array"}},"required":["review_id"]}')
ON CONFLICT (tool_name) DO NOTHING;
