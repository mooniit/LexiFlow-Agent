import {
  Badge,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  message,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { CheckOutlined, CloseOutlined, EditOutlined, EyeOutlined, InboxOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import {
  approveRequest,
  getApproval,
  getApprovalHistory,
  listApprovals,
  rejectRequest,
  requestRevision,
  type ApprovalHistory,
  type ApprovalRequest,
} from '../../api/approval';
import { getReview, getReviewRisks, type ContractReview, type ClauseRisk } from '../../api/review';
import { getContract, type Contract } from '../../api/contract';

const statusColors: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  REVISION_REQUESTED: 'blue',
  ESCALATED: 'purple',
  CANCELLED: 'default',
};
const statusLabels: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  REVISION_REQUESTED: '要求修改',
  ESCALATED: '已升级',
  CANCELLED: '已取消',
};

export default function ApprovalCenterPage() {
  const [approvals, setApprovals] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string | undefined>('PENDING');
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedApproval, setSelectedApproval] = useState<ApprovalRequest | null>(null);
  const [selectedReview, setSelectedReview] = useState<ContractReview | null>(null);
  const [selectedContract, setSelectedContract] = useState<Contract | null>(null);
  const [risks, setRisks] = useState<ClauseRisk[]>([]);
  const [history, setHistory] = useState<ApprovalHistory[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [actionComment, setActionComment] = useState('');
  const [acting, setActing] = useState(false);

  useEffect(() => {
    setLoading(true);
    listApprovals(statusFilter)
      .then(setApprovals)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [statusFilter]);

  async function openDetail(approval: ApprovalRequest) {
    setDetailVisible(true);
    setSelectedApproval(approval);
    setSelectedReview(null);
    setSelectedContract(null);
    setRisks([]);
    setHistory([]);
    setActionComment('');
    setDetailLoading(true);
    try {
      const [review, hist] = await Promise.all([
        getReview(approval.reviewId),
        getApprovalHistory(approval.id),
      ]);
      setSelectedReview(review);
      setHistory(hist);
      const [contract, riskList] = await Promise.all([
        getContract(review.contractId),
        getReviewRisks(review.id),
      ]);
      setSelectedContract(contract);
      setRisks(riskList);
    } catch {
      // ignore
    } finally {
      setDetailLoading(false);
    }
  }

  async function doAction(id: string, action: 'approve' | 'reject' | 'revision') {
    setActing(true);
    try {
      const updated = await (action === 'approve'
        ? approveRequest(id, actionComment)
        : action === 'reject'
          ? rejectRequest(id, actionComment)
          : requestRevision(id, actionComment));
      message.success(`${statusLabels[updated.status] || '操作成功'}`);
      setApprovals((prev) => prev.map((a) => (a.id === id ? updated : a)));
      setDetailVisible(false);
    } catch (err: any) {
      message.error(err.message || '操作失败');
    } finally {
      setActing(false);
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '审查任务 ID', dataIndex: 'reviewId', width: 100 },
    {
      title: '类型',
      dataIndex: 'approvalType',
      width: 100,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <Tag color={statusColors[v] || 'default'} className={v === 'PENDING' ? 'tag-pending' : ''}>{statusLabels[v] || v}</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '操作',
      width: 100,
      render: (_: unknown, record: ApprovalRequest) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => openDetail(record)}>
          查看
        </Button>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>审批中心</Typography.Title>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select
            value={statusFilter}
            onChange={setStatusFilter}
            style={{ width: 140 }}
            allowClear
            placeholder="状态筛选"
            options={Object.entries(statusLabels).map(([k, v]) => ({ label: v, value: k }))}
          />
        </Space>
        <Table rowKey="id" dataSource={approvals} columns={columns} loading={loading} pagination={{ pageSize: 10 }} />
      </Card>

      <Modal
        title={`审批详情 #${selectedApproval?.id || ''}`}
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        width={720}
        footer={null}
      >
        {detailLoading ? (
          <Spin style={{ display: 'block', margin: '40px auto' }} />
        ) : (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="审查任务">{selectedApproval?.reviewId}</Descriptions.Item>
              <Descriptions.Item label="审批类型">{selectedApproval?.approvalType}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColors[selectedApproval?.status || '']} className={selectedApproval?.status === 'PENDING' ? 'tag-pending' : ''}>
                  {statusLabels[selectedApproval?.status || '']}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="合同名称">
                {selectedContract?.contractName || '-'}
              </Descriptions.Item>
            </Descriptions>

            {risks.length > 0 && (
              <Card title="关联风险" size="small" style={{ marginBottom: 16 }}>
                {risks.map((r) => (
                  <Typography.Paragraph key={r.id} style={{ fontSize: 13, marginBottom: 8 }}>
                    <Tag color={r.riskLevel === 'HIGH' ? 'red' : r.riskLevel === 'MEDIUM' ? 'orange' : 'green'}>
                      {r.riskLevel}
                    </Tag>
                    <strong>{r.clauseName || r.riskType}</strong> — {r.reason}
                  </Typography.Paragraph>
                ))}
              </Card>
            )}

            {selectedApproval?.riskSummary && (
              <Card title="风险摘要" size="small" style={{ marginBottom: 16 }}>
                <pre style={{ fontSize: 12, whiteSpace: 'pre-wrap', margin: 0 }}>
                  {selectedApproval.riskSummary}
                </pre>
              </Card>
            )}

            {history.length > 0 && (
              <Card title="审批历史" size="small" style={{ marginBottom: 16 }}>
                <Timeline
                  items={history.map((h) => ({
                    children: (
                      <>
                        <Typography.Text strong>{h.action}</Typography.Text>
                        {h.comment && <Typography.Paragraph style={{ fontSize: 12, margin: 0 }}>{h.comment}</Typography.Paragraph>}
                        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                          {new Date(h.createdAt).toLocaleString()}
                        </Typography.Text>
                      </>
                    ),
                  }))}
                />
              </Card>
            )}

            {selectedApproval?.status === 'PENDING' && (
              <Card title="审批操作" size="small">
                <Input.TextArea
                  placeholder="审批意见（可选）"
                  value={actionComment}
                  onChange={(e) => setActionComment(e.target.value)}
                  rows={2}
                  style={{ marginBottom: 12 }}
                />
                <Space>
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    loading={acting}
                    onClick={() => doAction(selectedApproval!.id, 'approve')}
                  >
                    批准
                  </Button>
                  <Button
                    icon={<EditOutlined />}
                    loading={acting}
                    onClick={() => doAction(selectedApproval!.id, 'revision')}
                  >
                    要求修改
                  </Button>
                  <Button
                    danger
                    icon={<CloseOutlined />}
                    loading={acting}
                    onClick={() => doAction(selectedApproval!.id, 'reject')}
                  >
                    驳回
                  </Button>
                </Space>
              </Card>
            )}
          </>
        )}
      </Modal>
    </>
  );
}
