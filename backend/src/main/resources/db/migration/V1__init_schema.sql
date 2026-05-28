CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    department_id BIGINT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    role_id BIGINT NOT NULL REFERENCES role(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
    role_id BIGINT NOT NULL REFERENCES role(id),
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE contract (
    id BIGSERIAL PRIMARY KEY,
    contract_name VARCHAR(200) NOT NULL,
    contract_type VARCHAR(64),
    uploader_id BIGINT REFERENCES app_user(id),
    contract_amount NUMERIC(18, 2),
    customer_name VARCHAR(200),
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    file_type VARCHAR(20) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    parsed_text TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE contract_review (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES contract(id),
    status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    overall_risk_level VARCHAR(20),
    failure_reason TEXT,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    result_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE agent_step (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES contract_review(id),
    step_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE agent_state_transition_log (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES contract_review(id),
    from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL,
    reason TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    department_id BIGINT,
    allowed_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id),
    title VARCHAR(255) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    file_path VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE document_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_document(id),
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(1536),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE llm_call_log (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT REFERENCES contract_review(id),
    provider VARCHAR(64),
    model_name VARCHAR(128),
    prompt_version VARCHAR(64),
    request_body JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_body JSONB NOT NULL DEFAULT '{}'::jsonb,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms BIGINT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tool_call_log (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT REFERENCES contract_review(id),
    tool_name VARCHAR(128) NOT NULL,
    arguments JSONB NOT NULL DEFAULT '{}'::jsonb,
    result JSONB NOT NULL DEFAULT '{}'::jsonb,
    permission_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    latency_ms BIGINT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE retrieval_log (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT REFERENCES contract_review(id),
    query_text TEXT NOT NULL,
    filter_conditions JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieved_chunks JSONB NOT NULL DEFAULT '[]'::jsonb,
    latency_ms BIGINT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contract_review_contract_id ON contract_review(contract_id);
CREATE INDEX idx_agent_step_review_id ON agent_step(review_id);
CREATE INDEX idx_document_chunk_document_id ON document_chunk(document_id);
CREATE INDEX idx_document_chunk_embedding ON document_chunk USING ivfflat (embedding vector_cosine_ops);

INSERT INTO role (code, name) VALUES
('ADMIN', '管理员'),
('LEGAL_MANAGER', '法务主管'),
('LEGAL_REVIEWER', '法务人员'),
('BUSINESS_USER', '业务人员'),
('VIEWER', '只读用户');

