import { Button, Card, Form, Input, InputNumber, message, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import { Edit, Plus, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  createAdminUser,
  deleteAdminUser,
  listAdminRoles,
  listAdminUsers,
  updateAdminUser,
  type AdminRole,
  type AdminUser,
} from '../../api/admin';

type UserFormValue = {
  username?: string;
  password?: string;
  displayName: string;
  departmentId?: number;
  enabled: boolean;
  roles: string[];
};

export default function UserAdminPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<AdminUser | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<UserFormValue>();

  useEffect(() => {
    refresh();
  }, []);

  function refresh() {
    setLoading(true);
    Promise.all([listAdminUsers(), listAdminRoles()])
      .then(([userData, roleData]) => {
        setUsers(userData);
        setRoles(roleData);
      })
      .catch((err: Error) => message.error(err.message))
      .finally(() => setLoading(false));
  }

  function openCreate() {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ enabled: true, roles: ['BUSINESS_USER'] });
    setModalOpen(true);
  }

  function openEdit(user: AdminUser) {
    setEditing(user);
    form.setFieldsValue({
      username: user.username,
      displayName: user.displayName,
      departmentId: user.departmentId ? Number(user.departmentId) : undefined,
      enabled: user.enabled,
      roles: user.roles,
    });
    setModalOpen(true);
  }

  async function submit(values: UserFormValue) {
    setSaving(true);
    try {
      if (editing) {
        await updateAdminUser(editing.id, {
          password: values.password || undefined,
          displayName: values.displayName,
          departmentId: values.departmentId == null ? null : String(values.departmentId),
          enabled: values.enabled,
          roles: values.roles || [],
        });
      } else {
        await createAdminUser({
          username: values.username!,
          password: values.password!,
          displayName: values.displayName,
          departmentId: values.departmentId == null ? null : String(values.departmentId),
          enabled: values.enabled,
          roles: values.roles || [],
        });
      }
      message.success('用户已保存');
      setModalOpen(false);
      refresh();
    } catch (err: any) {
      message.error(err.message || '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function remove(user: AdminUser) {
    try {
      await deleteAdminUser(user.id);
      message.success('用户已停用并删除');
      refresh();
    } catch (err: any) {
      message.error(err.message || '删除失败');
    }
  }

  const columns = [
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '显示名', dataIndex: 'displayName', width: 160 },
    { title: '部门', dataIndex: 'departmentId', width: 100, render: (v: string | null) => v || '-' },
    {
      title: '角色',
      dataIndex: 'roles',
      render: (value: string[]) => <Space wrap>{value.map((role) => <Tag key={role}>{role}</Tag>)}</Space>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '停用'}</Tag>,
    },
    {
      title: '操作',
      width: 150,
      render: (_: unknown, record: AdminUser) => (
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
        <Typography.Title level={4} style={{ margin: 0 }}>用户与权限</Typography.Title>
        <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>新建用户</Button>
      </div>
      <Card>
        <Table rowKey="id" dataSource={users} columns={columns} loading={loading} pagination={{ pageSize: 10 }} />
      </Card>

      <Modal
        title={editing ? '编辑用户' : '新建用户'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical" onFinish={submit} initialValues={{ enabled: true }}>
          {!editing && (
            <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input />
            </Form.Item>
          )}
          <Form.Item name="displayName" label="显示名" rules={[{ required: true, message: '请输入显示名' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="password"
            label={editing ? '重置密码' : '密码'}
            rules={editing ? [] : [{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder={editing ? '留空则不修改' : undefined} />
          </Form.Item>
          <Form.Item name="departmentId" label="部门 ID">
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="roles" label="角色">
            <Select
              mode="multiple"
              options={roles.map((role) => ({ value: role.code, label: `${role.name} (${role.code})` }))}
            />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
