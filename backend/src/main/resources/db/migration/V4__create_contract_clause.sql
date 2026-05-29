CREATE TABLE contract_clause (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES contract(id),
    clause_name VARCHAR(200) NOT NULL,
    clause_type VARCHAR(64),
    clause_text TEXT NOT NULL,
    structured_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    sequence_order INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE contract_clause IS '合同条款抽取结果';
COMMENT ON COLUMN contract_clause.clause_type IS '条款类型：parties/amount/payment_term/duration/liability/confidentiality/ip/data_protection/dispute_resolution/termination/indemnity_cap/auto_renewal 等';
COMMENT ON COLUMN contract_clause.structured_data IS 'LLM 结构化抽取结果，如主体信息、金额、日期等字段';
COMMENT ON COLUMN contract_clause.sequence_order IS '条款在合同中的出现顺序';

CREATE INDEX IF NOT EXISTS idx_contract_clause_contract_id ON contract_clause(contract_id);
CREATE INDEX IF NOT EXISTS idx_contract_clause_clause_type ON contract_clause(clause_type);
