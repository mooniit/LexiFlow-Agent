-- ============================================================
-- DB#12: knowledge_base & knowledge_document 索引
-- DB#13: document_chunk metadata 索引
-- DB#18: JSONB GIN 索引
-- ============================================================

-- knowledge_base (DB#12)
CREATE INDEX IF NOT EXISTS idx_knowledge_base_visibility ON knowledge_base(visibility);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_status ON knowledge_base(status);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_department_id ON knowledge_base(department_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_allowed_roles ON knowledge_base USING GIN (allowed_roles);

-- knowledge_document (DB#12)
CREATE INDEX IF NOT EXISTS idx_knowledge_document_kb_id ON knowledge_document(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_type ON knowledge_document(document_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_status ON knowledge_document(document_status);

-- document_chunk metadata filter (DB#13)
CREATE INDEX IF NOT EXISTS idx_document_chunk_metadata ON document_chunk USING GIN (metadata);

-- JSONB GIN indexes for semi-structured querying (DB#18)
-- contract: metadata 中包含自定义字段，如部门、标签、自定义属性
CREATE INDEX IF NOT EXISTS idx_contract_metadata ON contract USING GIN (metadata);

-- contract_review: result_summary JSONB 支持按风险分布、条款数量查询
CREATE INDEX IF NOT EXISTS idx_contract_review_result_summary ON contract_review USING GIN (result_summary);

-- agent_step: input_summary / output_summary 查询步骤输入输出内容
CREATE INDEX IF NOT EXISTS idx_agent_step_input_summary ON agent_step USING GIN (input_summary);
CREATE INDEX IF NOT EXISTS idx_agent_step_output_summary ON agent_step USING GIN (output_summary);

-- llm_call_log: request_body / response_body 查询模型请求响应内容
CREATE INDEX IF NOT EXISTS idx_llm_call_log_request_body ON llm_call_log USING GIN (request_body);
CREATE INDEX IF NOT EXISTS idx_llm_call_log_response_body ON llm_call_log USING GIN (response_body);

-- knowledge_document: metadata GIN
CREATE INDEX IF NOT EXISTS idx_knowledge_document_metadata ON knowledge_document USING GIN (metadata);
