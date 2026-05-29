import {
  Button,
  Card,
  Input,
  message,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, EyeOutlined, PlayCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { deleteContract, listContracts, type Contract } from '../../api/contract';
import { createReview } from '../../api/review';

export default function ContractListPage() {
  const [contracts, setContracts] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [search, setSearch] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true);
    listContracts(statusFilter)
      .then(setContracts)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [statusFilter]);

  async function handleCreateReview(contractId: string) {
    try {
      const review = await createReview(contractId);
      message.success(`审查任务已创建 #${review.id}`);
      navigate(`/contracts/${contractId}?reviewId=${review.id}`);
    } catch (err: any) {
      message.error(err.message || '创建审查失败');
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteContract(id);
      message.success('合同已删除');
      setContracts((prev) => prev.filter((c) => c.id !== id));
    } catch (err: any) {
      message.error(err.message || '删除失败');
    }
  }

  const filtered = contracts.filter((c) => {
    if (!search) return true;
    const s = search.toLowerCase();
    return (
      (c.contractName || '').toLowerCase().includes(s) ||
      (c.customerName || '').toLowerCase().includes(s) ||
      String(c.id).includes(s)
    );
  });

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
    { title: '类型', dataIndex: 'contractType', width: 100 },
    { title: '客户', dataIndex: 'customerName', width: 120 },
    {
      title: '金额',
      dataIndex: 'contractAmount',
      width: 120,
      render: (v: number) => (v ? `¥${v.toLocaleString()}` : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '操作',
      width: 180,
      render: (_: unknown, record: Contract) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/contracts/${record.id}`)}>
            查看
          </Button>
          <Button size="small" type="primary" icon={<PlayCircleOutlined />} onClick={() => handleCreateReview(record.id)}>
            审查
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>合同管理</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/contracts/upload')}>
          上传合同
        </Button>
      </div>
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="搜索名称/客户/ID"
            allowClear
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 240 }}
          />
          <Select
            placeholder="状态筛选"
            allowClear
            value={statusFilter}
            onChange={setStatusFilter}
            style={{ width: 140 }}
            options={[
              { label: '已上传', value: 'UPLOADED' },
              { label: '已解析', value: 'PARSED' },
              { label: '已归档', value: 'ARCHIVED' },
            ]}
          />
        </Space>
        <Table
          rowKey="id"
          dataSource={filtered}
          columns={columns}
          loading={loading}
          pagination={{ pageSize: 10 }}
          onRow={(record) => ({
            onDoubleClick: () => navigate(`/contracts/${record.id}`),
          })}
        />
      </Card>
    </>
  );
}
