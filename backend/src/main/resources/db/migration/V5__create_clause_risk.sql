CREATE TABLE clause_risk (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES contract_review(id),
    clause_id BIGINT REFERENCES contract_clause(id),
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    risk_type VARCHAR(64) NOT NULL,
    clause_name VARCHAR(200),
    clause_text TEXT,
    reason TEXT,
    suggestion TEXT,
    evidence_rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    review_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE clause_risk IS '条款风险识别结果';
COMMENT ON COLUMN clause_risk.risk_level IS '风险等级：LOW/MEDIUM/HIGH';
COMMENT ON COLUMN clause_risk.risk_type IS '风险类型：PAYMENT_TERM_TOO_LONG/UNLIMITED_LIABILITY/MISSING_DATA_PROTECTION/NON_STANDARD_JURISDICTION/AUTO_RENEWAL_RISK/ONE_SIDED_TERMINATION/CONFIDENTIALITY_WEAK/IP_OWNERSHIP_UNCLEAR/HIGH_CONTRACT_AMOUNT/MISSING_TERMINATION_CLAUSE';
COMMENT ON COLUMN clause_risk.evidence_rules IS '引用的合规规则依据，包含规则 ID、标题和片段';
COMMENT ON COLUMN clause_risk.review_status IS '风险处理状态：OPEN/ACKNOWLEDGED/RESOLVED/DISMISSED';

CREATE INDEX IF NOT EXISTS idx_clause_risk_review_id ON clause_risk(review_id);
CREATE INDEX IF NOT EXISTS idx_clause_risk_clause_id ON clause_risk(clause_id);
CREATE INDEX IF NOT EXISTS idx_clause_risk_risk_level ON clause_risk(risk_level);
CREATE INDEX IF NOT EXISTS idx_clause_risk_risk_type ON clause_risk(risk_type);
CREATE INDEX IF NOT EXISTS idx_clause_risk_requires_approval ON clause_risk(requires_approval);
