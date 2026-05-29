-- ============================================================
-- DB#17: 种子规则数据
-- ============================================================

-- 创建默认合规知识库
INSERT INTO knowledge_base (name, visibility, status) VALUES
('公司合规规则库', 'PUBLIC', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 插入种子规则文档（通过子查询获取 knowledge_base_id）
DO $$
DECLARE
    v_kb_id BIGINT;
BEGIN
    SELECT id INTO v_kb_id FROM knowledge_base WHERE name = '公司合规规则库' LIMIT 1;
    IF v_kb_id IS NULL THEN
        RETURN;
    END IF;

    -- 公司合同审查规范
    INSERT INTO knowledge_document (knowledge_base_id, title, document_type, document_status) VALUES
    (v_kb_id, '公司合同审查规范', 'POLICY', 'PUBLISHED')
    ON CONFLICT DO NOTHING;

    INSERT INTO knowledge_document (knowledge_base_id, title, document_type, document_status) VALUES
    (v_kb_id, '销售合同标准模板', 'TEMPLATE', 'PUBLISHED')
    ON CONFLICT DO NOTHING;

    INSERT INTO knowledge_document (knowledge_base_id, title, document_type, document_status) VALUES
    (v_kb_id, '采购合同标准模板', 'TEMPLATE', 'PUBLISHED')
    ON CONFLICT DO NOTHING;

    INSERT INTO knowledge_document (knowledge_base_id, title, document_type, document_status) VALUES
    (v_kb_id, '数据保护条款指南', 'GUIDELINE', 'PUBLISHED')
    ON CONFLICT DO NOTHING;

    INSERT INTO knowledge_document (knowledge_base_id, title, document_type, document_status) VALUES
    (v_kb_id, '付款周期审批规则', 'POLICY', 'PUBLISHED')
    ON CONFLICT DO NOTHING;

    INSERT INTO knowledge_document (knowledge_base_id, title, document_type, document_status) VALUES
    (v_kb_id, '违约责任审查规则', 'POLICY', 'PUBLISHED')
    ON CONFLICT DO NOTHING;
END $$;
