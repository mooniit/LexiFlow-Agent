INSERT INTO prompt_template (name, version, scene, description, template_content, variables, output_constraints, created_by, updated_by)
VALUES
('clause-extraction-default', 'v1', 'CLAUSE_EXTRACTION', '合同条款抽取默认模板',
'请从合同文本中抽取关键条款，输出条款名称、原文片段和结构化字段。

合同文本：
{{contract_text}}',
'[{"name":"contract_text","required":true}]'::jsonb,
'{"format":"json"}'::jsonb, 1, 1),
('risk-analysis-default', 'v1', 'RISK_ANALYSIS', '风险分析默认模板',
'请基于合同条款和合规规则识别风险，不要使用未提供的依据。

合同条款：
{{clauses}}

合规规则：
{{rules}}',
'[{"name":"clauses","required":true},{"name":"rules","required":true}]'::jsonb,
'{"format":"json"}'::jsonb, 1, 1),
('report-generation-default', 'v1', 'REPORT_GENERATION', '审查报告生成默认模板',
'请根据审查上下文生成摘要、风险结论、修改建议和审批说明。

审查上下文：
{{review_context}}',
'[{"name":"review_context","required":true}]'::jsonb,
'{"format":"markdown"}'::jsonb, 1, 1),
('knowledge-qa-default', 'v1', 'KNOWLEDGE_QA', '知识库问答默认模板',
'你是企业合规知识库助手。只能基于知识库片段回答，不要编造规则。

问题：
{{question}}

知识库片段：
{{context}}

请给出简洁答案，并列出依据。',
'[{"name":"question","required":true},{"name":"context","required":true}]'::jsonb,
'{"format":"text","grounded":true}'::jsonb, 1, 1)
ON CONFLICT DO NOTHING;
