import { Badge, Button, Card, Form, Input, message, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import { Edit, Plus, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  createReviewTool,
  deleteReviewTool,
  listReviewTools,
  updateReviewTool,
  type ReviewToolConfig,
  type SaveToolPayload,
} from '../../api/toolConfig';

export default function ToolConfigPage() {
  const [tools, setTools] = useState<ReviewToolConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<ReviewToolConfig | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<SaveToolPayload>();

  useEffect(() => {
    refresh();
  }, []);

  function refresh() {
    setLoading(true);
    listReviewTools()
      .then(setTools)
      .catch((err: Error) => message.error(err.message))
      .finally(() => setLoading(false));
  }

  function openCreate() {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ riskLevel: 'LOW', approvalRequired: false, enabled: true, requiredPermission: 'tool:execute' });
    setModalOpen(true);
  }

  function openEdit(tool: ReviewToolConfig) {
    setEditing(tool);
    form.setFieldsValue(tool);
    setModalOpen(true);
  }

  async function submit(values: SaveToolPayload) {
    setSaving(true);
    try {
      if (editing) {
        await updateReviewTool(editing.id, values);
      } else {
        await createReviewTool(values);
      }
      message.success('工具配置已保存');
      setModalOpen(false);
      refresh();
    } catch (err: any) {
      message.error(err.message || '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function remove(tool: ReviewToolConfig) {
    try {
      await deleteReviewTool(tool.id);
      message.success('工具已禁用并删除');
      refresh();
    } catch (err: any) {
      message.error(err.message || '删除失败');
    }
  }

  const columns = [
    { title: '工具名', dataIndex: 'toolName', width: 180 },
    { title: '权限', dataIndex: 'requiredPermission', width: 150 },
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      width: 100,
      render: (value: string) => {
        const labels: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' };
        const colors: Record<string, string> = { HIGH: 'red', MEDIUM: 'orange', LOW: 'green' };
        return <Tag color={colors[value]}>{labels[value] || value}</Tag>;
      },
    },
    {
      title: '审批',
      dataIndex: 'approvalRequired',
      width: 90,
      render: (value: boolean) => <Tag color={value ? 'orange' : 'default'}>{value ? '需要' : '不需要'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value: boolean) => <Tag color={value ? 'green' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
    {
      title: '操作',
      width: 150,
      render: (_: unknown, record: ReviewToolConfig) => (
        <Space>
          <Button size="small" icon={<Edit size={14} />} onClick={() => openEdit(record)} />
          <Button size="small" danger icon={<Trash2 size={14} />} onClick={() => remove(record)} />
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>审查工具配置</Typography.Title>
        <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>新建工具</Button>
      </div>
      <Card>
        <Table rowKey="id" dataSource={tools} columns={columns} loading={loading} pagination={{ pageSize: 10 }} />
      </Card>

      <Modal
        title={editing ? '编辑工具' : '新建工具'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="toolName" label="工具名" rules={[{ required: true, message: '请输入工具名' }]}>
            <Input disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item name="requiredPermission" label="所需权限">
            <Input placeholder="tool:execute" />
          </Form.Item>
          <Form.Item name="riskLevel" label="风险等级" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'LOW', label: '低风险' },
                { value: 'MEDIUM', label: '中风险' },
                { value: 'HIGH', label: '高风险' },
              ]}
            />
          </Form.Item>
          <Form.Item name="approvalRequired" label="需要审批" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
