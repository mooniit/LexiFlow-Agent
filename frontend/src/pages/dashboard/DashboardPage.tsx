import { Card, List, Progress, Skeleton, Statistic, Tag, Timeline, Typography, Empty, Button } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { AlertTriangle, CheckCircle, Clock, FileText } from 'lucide-react';
import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { listReviews, type ContractReview } from '../../api/review';
import { listContracts, type Contract } from '../../api/contract';

export default function DashboardPage() {
  const [reviews, setReviews] = useState<ContractReview[]>([]);
  const [contracts, setContracts] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const navigate = useNavigate();

  function load() {
    setLoading(true);
    setError(false);
    Promise.all([listReviews(), listContracts()])
      .then(([r, c]) => { setReviews(r); setContracts(c); })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  const pendingReviews = reviews.filter((r) => r.status === 'WAITING_APPROVAL').length;
  const highRiskCount = reviews.filter((r) => r.overallRisk === 'HIGH').length;

  const contractNames = useMemo(() => {
    const map: Record<string, string> = {};
    contracts.forEach((c) => { map[c.id] = c.contractName || `合同 #${c.id}`; });
    return map;
  }, [contracts]);

  const statusLabels: Record<string, string> = {
    CREATED: '已创建', PARSING: '解析中', EXTRACTING: '条款抽取',
    RETRIEVING_RULES: '规则检索', ANALYZING: '风险分析', WAITING_APPROVAL: '等待审批',
    GENERATING_REPORT: '生成报告', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消',
  };

  const statusOrder = ['CREATED', 'PARSING', 'EXTRACTING', 'RETRIEVING_RULES', 'ANALYZING', 'WAITING_APPROVAL', 'GENERATING_REPORT', 'COMPLETED'];

  function reviewProgress(status: string): number {
    if (status === 'FAILED') return 0;
    if (status === 'CANCELLED') return 0;
    const idx = statusOrder.indexOf(status);
    return idx >= 0 ? Math.round((idx / (statusOrder.length - 1)) * 100) : 0;
  }

  if (error) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 80 }}>
        <Empty description="数据加载失败" />
        <Button type="primary" onClick={load} style={{ marginTop: 12 }}>重新加载</Button>
      </div>
    );
  }

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>工作台</Typography.Title>
      <section className="metric-grid">
        <Card className="stat-total">
          {loading ? <Skeleton active paragraph={{ rows: 1 }} /> :
            <Statistic title="审查任务" value={reviews.length} prefix={<FileText size={18} />} />
          }
        </Card>
        <Card className="stat-pending">
          {loading ? <Skeleton active paragraph={{ rows: 1 }} /> :
            <Statistic title="待审批" value={pendingReviews} prefix={<Clock size={18} />} valueStyle={pendingReviews > 0 ? { color: '#b8864e' } : undefined} />
          }
        </Card>
        <Card className="stat-high">
          {loading ? <Skeleton active paragraph={{ rows: 1 }} /> :
            <Statistic title="高风险" value={highRiskCount} prefix={<AlertTriangle size={18} />} valueStyle={highRiskCount > 0 ? { color: '#b85c5c' } : undefined} />
          }
        </Card>
      </section>
      <section className="main-grid">
        <Card title="最近审查任务">
          {loading ? (
            <Skeleton active paragraph={{ rows: 4 }} />
          ) : reviews.length === 0 ? (
            <Empty description="暂无审查记录">
              <Button type="primary" icon={<PlusOutlined size={14} />} onClick={() => navigate('/contracts')}>
                去审查合同
              </Button>
            </Empty>
          ) : (
            <List
              dataSource={reviews.slice(0, 6)}
              renderItem={(r) => (
                <List.Item
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/contracts/${r.contractId}?reviewId=${r.id}`)}
                >
                  <List.Item.Meta
                    title={
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Typography.Text strong>{contractNames[r.contractId] || `合同 #${r.contractId}`}</Typography.Text>
                        <Tag color={r.status === 'COMPLETED' ? 'green' : r.status === 'FAILED' ? 'red' : r.status === 'WAITING_APPROVAL' ? 'orange' : 'blue'}>
                          {statusLabels[r.status] || r.status}
                        </Tag>
                      </div>
                    }
                    description={
                      <div style={{ marginTop: 4 }}>
                        <Progress
                          percent={reviewProgress(r.status)}
                          size="small"
                          strokeColor={r.status === 'COMPLETED' ? '#52c41a' : r.status === 'FAILED' ? '#ff4d4f' : '#1677ff'}
                          format={() => ''}
                          style={{ marginBottom: 4 }}
                        />
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {new Date(r.createdAt).toLocaleString()}
                        </Typography.Text>
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          )}
        </Card>
        <Card title="Agent 执行流程">
          <Timeline
            items={[
              { dot: <CheckCircle size={14} />, children: '合同上传' },
              { children: '条款抽取' },
              { children: '规则检索' },
              { children: '风险分析' },
              { children: '人工审批' },
              { children: '报告生成' },
            ]}
          />
        </Card>
      </section>
      <section style={{ marginTop: 16 }}>
        <Card title="合同概览">
          {loading ? (
            <Skeleton active paragraph={{ rows: 3 }} />
          ) : contracts.length === 0 ? (
            <Empty description="暂无合同">
              <Button type="primary" icon={<PlusOutlined size={14} />} onClick={() => navigate('/contracts/upload')}>
                上传第一份合同
              </Button>
            </Empty>
          ) : (
            <List
              dataSource={contracts.slice(0, 5)}
              renderItem={(c) => (
                <List.Item
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/contracts/${c.id}`)}
                >
                  <List.Item.Meta
                    title={c.contractName || `合同 #${c.id}`}
                    description={`客户: ${c.customerName || '-'} | 金额: ${c.contractAmount || '-'} | 状态: ${c.status}`}
                  />
                </List.Item>
              )}
            />
          )}
        </Card>
      </section>
    </>
  );
}
