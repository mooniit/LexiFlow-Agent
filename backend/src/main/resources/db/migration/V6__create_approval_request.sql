CREATE TABLE approval_request (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES contract_review(id),
    approval_type VARCHAR(32) NOT NULL DEFAULT 'REVIEW',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    approver_id BIGINT REFERENCES app_user(id),
    requested_by BIGINT REFERENCES app_user(id),
    comment TEXT,
    risk_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    resolved_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE approval_request IS '人工审批请求';
COMMENT ON COLUMN approval_request.approval_type IS '审批类型：REVIEW（审查任务审批）/CLAUSE（单条款审批）/REPORT（报告审批）';
COMMENT ON COLUMN approval_request.status IS '审批状态：PENDING/APPROVED/REJECTED/REVISION_REQUESTED/ESCALATED/CANCELLED';
COMMENT ON COLUMN approval_request.risk_summary IS '触发审批的风险摘要，包含风险等级、类型和关键条款';

CREATE INDEX IF NOT EXISTS idx_approval_request_review_id ON approval_request(review_id);
CREATE INDEX IF NOT EXISTS idx_approval_request_approver_id ON approval_request(approver_id);
CREATE INDEX IF NOT EXISTS idx_approval_request_status ON approval_request(status);
CREATE INDEX IF NOT EXISTS idx_approval_request_created_at ON approval_request(created_at);

CREATE TABLE approval_history (
    id BIGSERIAL PRIMARY KEY,
    approval_request_id BIGINT NOT NULL REFERENCES approval_request(id),
    action VARCHAR(32) NOT NULL,
    operator_id BIGINT REFERENCES app_user(id),
    comment TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE approval_history IS '审批操作历史';
COMMENT ON COLUMN approval_history.action IS '操作类型：SUBMIT/APPROVE/REJECT/REQUEST_REVISION/ESCALATE/CANCEL/REASSIGN';

CREATE INDEX IF NOT EXISTS idx_approval_history_request_id ON approval_history(approval_request_id);
CREATE INDEX IF NOT EXISTS idx_approval_history_created_at ON approval_history(created_at);
