CREATE INDEX IF NOT EXISTS idx_contract_uploader_id ON contract(uploader_id);
CREATE INDEX IF NOT EXISTS idx_contract_status ON contract(status);
CREATE INDEX IF NOT EXISTS idx_contract_contract_type ON contract(contract_type);
CREATE INDEX IF NOT EXISTS idx_contract_customer_name ON contract(customer_name);
CREATE INDEX IF NOT EXISTS idx_contract_created_at ON contract(created_at);

CREATE INDEX IF NOT EXISTS idx_contract_review_status ON contract_review(status);
CREATE INDEX IF NOT EXISTS idx_contract_review_overall_risk_level ON contract_review(overall_risk_level);
CREATE INDEX IF NOT EXISTS idx_contract_review_created_by ON contract_review(created_by);
CREATE INDEX IF NOT EXISTS idx_contract_review_created_at ON contract_review(created_at);
