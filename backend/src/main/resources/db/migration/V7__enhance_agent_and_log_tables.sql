-- DB#08: agent_step & agent_state_transition_log indexes
CREATE INDEX IF NOT EXISTS idx_agent_step_step_type ON agent_step(step_type);
CREATE INDEX IF NOT EXISTS idx_agent_step_status ON agent_step(status);
CREATE INDEX IF NOT EXISTS idx_agent_step_started_at ON agent_step(started_at);
CREATE INDEX IF NOT EXISTS idx_agent_state_transition_log_review_id ON agent_state_transition_log(review_id);
CREATE INDEX IF NOT EXISTS idx_agent_state_transition_log_created_at ON agent_state_transition_log(created_at);

-- DB#09: llm_call_log indexes
CREATE INDEX IF NOT EXISTS idx_llm_call_log_review_id ON llm_call_log(review_id);
CREATE INDEX IF NOT EXISTS idx_llm_call_log_provider ON llm_call_log(provider);
CREATE INDEX IF NOT EXISTS idx_llm_call_log_model_name ON llm_call_log(model_name);
CREATE INDEX IF NOT EXISTS idx_llm_call_log_success ON llm_call_log(success);
CREATE INDEX IF NOT EXISTS idx_llm_call_log_created_at ON llm_call_log(created_at);

-- DB#10: tool_call_log indexes
CREATE INDEX IF NOT EXISTS idx_tool_call_log_review_id ON tool_call_log(review_id);
CREATE INDEX IF NOT EXISTS idx_tool_call_log_tool_name ON tool_call_log(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_call_log_success ON tool_call_log(success);
CREATE INDEX IF NOT EXISTS idx_tool_call_log_created_at ON tool_call_log(created_at);

-- DB#11: retrieval_log indexes
CREATE INDEX IF NOT EXISTS idx_retrieval_log_review_id ON retrieval_log(review_id);
CREATE INDEX IF NOT EXISTS idx_retrieval_log_created_at ON retrieval_log(created_at);
