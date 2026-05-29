CREATE TABLE review_tool_config (
    id BIGSERIAL PRIMARY KEY,
    tool_name VARCHAR(128) NOT NULL UNIQUE,
    required_permission VARCHAR(128) NOT NULL,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW',
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_review_tool_config_enabled ON review_tool_config(enabled);
CREATE INDEX idx_review_tool_config_permission ON review_tool_config(required_permission);

INSERT INTO review_tool_config (tool_name, required_permission, risk_level, approval_required, enabled)
VALUES
('contract_parse', 'tool:execute', 'LOW', FALSE, TRUE),
('clause_extraction', 'tool:execute', 'LOW', FALSE, TRUE),
('rule_retrieval', 'knowledge:read', 'MEDIUM', FALSE, TRUE),
('risk_analysis', 'tool:execute', 'MEDIUM', FALSE, TRUE),
('approval_request', 'approval:write', 'HIGH', TRUE, TRUE)
ON CONFLICT (tool_name) DO NOTHING;

