import { Button, Card, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import {
  createPrompt,
  deletePrompt,
  listPrompts,
  updatePrompt,
  type PromptTemplate,
  type PromptTemplatePayload,
} from '../../api/prompt';

const SCENE_LABELS: Record<string, string> = {
  CLAUSE_EXTRACTION: '条款抽取',
  RISK_ANALYSIS: '风险分析',
  RULE_EXPLANATION: '规则解释',
  SUGGESTION_GENERATION: '修改建议',
  REPORT_GENERATION: '报告生成',
  KNOWLEDGE_QA: '知识库问答',
};

export default function PromptTemplatePage() {
  const [items, setItems] = useState<PromptTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<PromptTemplate | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<PromptTemplatePayload>();

  function load() {
    setLoading(true);
    listPrompts()
      .then(setItems)
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  function showEditor(record?: PromptTemplate) {
    setEditing(record || null);
    form.setFieldsValue(record ? {
      name: record.name,
      version: record.version,
      scene: record.scene,
      description: record.description,
      templateContent: record.templateContent,
      variables: record.variables,
      outputConstraints: record.outputConstraints,
      enabled: record.enabled,
    } : {
      version: 'v1',
      scene: 'KNOWLEDGE_QA',
      variables: '[]',
      outputConstraints: '{}',
      enabled: true,
    });
    setOpen(true);
  }

  async function submit() {
    const values = await form.validateFields();
    if (editing) {
      await updatePrompt(editing.id, values);
      message.success('模板已更新');
    } else {
      await createPrompt(values);
      message.success('模板已创建');
    }
    setOpen(false);
    load();
  }

  async function remove(id: string) {
    await deletePrompt(id);
    message.success('模板已删除');
    load();
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Prompt 模板</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => showEditor()}>新建模板</Button>
      </div>

      <Card>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={items}
          pagination={false}
          columns={[
            { title: '名称', dataIndex: 'name' },
            { title: '版本', dataIndex: 'version', width: 100 },
            { title: '场景', dataIndex: 'scene', render: (v: string) => <Tag>{SCENE_LABELS[v] || v}</Tag> },
            { title: '状态', dataIndex: 'enabled', width: 90, render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? '启用' : '停用'}</Tag> },
            { title: '说明', dataIndex: 'description', ellipsis: true },
            {
              title: '操作',
              width: 150,
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => showEditor(record)}>编辑</Button>
                  <Button size="small" danger onClick={() => remove(record.id)}>删除</Button>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal open={open} title={editing ? '编辑模板' : '新建模板'} onCancel={() => setOpen(false)} onOk={submit} width={760}>
        <Form form={form} layout="vertical">
          <Space style={{ width: '100%' }} align="start">
            <Form.Item name="name" label="名称" rules={[{ required: true }]} style={{ width: 230 }}>
              <Input />
            </Form.Item>
            <Form.Item name="version" label="版本" rules={[{ required: true }]} style={{ width: 120 }}>
              <Input />
            </Form.Item>
            <Form.Item name="scene" label="场景" rules={[{ required: true }]} style={{ width: 220 }}>
              <Select options={Object.entries(SCENE_LABELS).map(([k, v]) => ({ label: v, value: k }))} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="说明">
            <Input />
          </Form.Item>
          <Form.Item name="templateContent" label="模板正文" rules={[{ required: true }]}>
            <Input.TextArea rows={10} />
          </Form.Item>
          <Form.Item name="variables" label="变量定义 JSON">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="outputConstraints" label="输出约束 JSON">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
