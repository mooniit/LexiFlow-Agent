import { Card, List, Statistic, Tag, Timeline, Typography, Spin, Empty } from 'antd';
import { AlertTriangle, CheckCircle, Clock, FileText } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listReviews, type ContractReview } from '../../api/review';
import { listContracts, type Contract } from '../../api/contract';

export default function DashboardPage() {
  const [reviews, setReviews] = useState<ContractReview[]>([]);
  const [contracts, setContracts] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    Promise.all([listReviews(), listContracts()])
      .then(([r, c]) => {
        setReviews(r);
        setContracts(c);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const pendingReviews = reviews.filter((r) => r.status === 'WAITING_APPROVAL').length;
  const highRiskCount = reviews.filter((r) => r.overallRisk === 'HIGH').length;
  const completedCount = reviews.filter((r) => r.status === 'COMPLETED').length;

  const statusLabels: Record<string, string> = {
    CREATED: '已创建',
    PARSING: '解析中',
    EXTRACTING: '条款抽取',
    RETRIEVING_RULES: '规则检索',
    ANALYZING: '风险分析',
    WAITING_APPROVAL: '等待审批',
    GENERATING_REPORT: '生成报告',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  };

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} size="large" />;

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>工作台</Typography.Title>
      <section className="metric-grid">
        <Card>
          <Statistic title="审查任务" value={reviews.length} prefix={<FileText size={18} />} />
        </Card>
        <Card>
          <Statistic title="待审批" value={pendingReviews} prefix={<Clock size={18} />} valueStyle={pendingReviews > 0 ? { color: '#cf1322' } : undefined} />
        </Card>
        <Card>
          <Statistic title="高风险" value={highRiskCount} prefix={<AlertTriangle size={18} />} valueStyle={highRiskCount > 0 ? { color: '#cf1322' } : undefined} />
        </Card>
      </section>
      <section className="main-grid">
        <Card title="最近审查任务">
          {reviews.length === 0 ? (
            <Empty description="暂无审查记录" />
          ) : (
            <List
              dataSource={reviews.slice(0, 6)}
              renderItem={(r) => (
                <List.Item
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/contracts/${r.contractId}?reviewId=${r.id}`)}
                  extra={
                    <Tag color={r.status === 'COMPLETED' ? 'green' : r.status === 'FAILED' ? 'red' : r.status === 'WAITING_APPROVAL' ? 'orange' : 'blue'}>
                      {statusLabels[r.status] || r.status}
                    </Tag>
                  }
                >
                  <List.Item.Meta
                    title={`审查 #${r.id}`}
                    description={`合同 ID: ${r.contractId} | ${new Date(r.createdAt).toLocaleString()}`}
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
          {contracts.length === 0 ? (
            <Empty description="暂无合同" />
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
