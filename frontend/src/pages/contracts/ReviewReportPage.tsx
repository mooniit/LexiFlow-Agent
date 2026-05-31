import { Alert, Button, Card, Descriptions, Empty, Spin, Tag, Timeline, Typography } from 'antd';
import { BranchesOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { getReviewReport, getReviewSteps, type AgentStep, type ReviewReport } from '../../api/review';
import RiskList from '../../components/RiskList';

const riskColor: Record<string, string> = { LOW: 'green', MEDIUM: 'orange', HIGH: 'red' };
const riskLabel: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' };
const statusLabels: Record<string, string> = {
  PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回',
  REVISION_REQUESTED: '要求修改', ESCALATED: '已升级', CANCELLED: '已取消',
};

export default function ReviewReportPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const contractId = id || '';
  const reviewId = searchParams.get('reviewId');
  const [report, setReport] = useState<ReviewReport | null>(null);
  const [steps, setSteps] = useState<AgentStep[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      if (!reviewId) {
        setLoading(false);
        return;
      }
      try {
        const [reportData, stepList] = await Promise.all([
          getReviewReport(reviewId),
          getReviewSteps(reviewId),
        ]);
        setReport(reportData);
        setSteps(stepList);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [reviewId]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} size="large" />;
  if (!report) return <Empty description="未找到审查报告" />;

  const review = report.review;
  const risks = report.risks;
  const highRisks = risks.filter((r) => r.riskLevel === 'HIGH').length;
  const mediumRisks = risks.filter((r) => r.riskLevel === 'MEDIUM').length;
  const lowRisks = risks.filter((r) => r.riskLevel === 'LOW').length;
  const approvedCount = report.approvals.filter((a) => a.status === 'APPROVED').length;
  const pendingCount = report.approvals.filter((a) => a.status === 'PENDING').length;

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          审查报告 - {report.contract.contractName || `合同 #${contractId}`}
        </Typography.Title>
        <Button icon={<BranchesOutlined />} onClick={() => navigate(`/contracts/${contractId}/trace?reviewId=${review.id}`)}>
          Agent Trace
        </Button>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 16 }}>
          <Typography.Title level={3} style={{ margin: 0 }}>总体风险等级：</Typography.Title>
          <Tag color={riskColor[review.overallRiskLevel || '']} style={{ fontSize: 20, padding: '4px 20px' }}>
            {riskLabel[review.overallRiskLevel || ''] || review.overallRiskLevel || '未知'}
          </Tag>
        </div>
        <Descriptions column={4} size="small">
          <Descriptions.Item label="高风险"><Typography.Text strong type="danger">{highRisks}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="中风险"><Typography.Text strong type="warning">{mediumRisks}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="低风险"><Typography.Text strong type="success">{lowRisks}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="审查状态"><Tag>{review.status}</Tag></Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="基础信息" style={{ marginBottom: 16 }}>
        <Descriptions column={3} size="small">
          <Descriptions.Item label="合同类型">{report.contract.contractType || '-'}</Descriptions.Item>
          <Descriptions.Item label="客户">{report.contract.customerName || '-'}</Descriptions.Item>
          <Descriptions.Item label="金额">{report.contract.contractAmount ? `¥${report.contract.contractAmount.toLocaleString()}` : '-'}</Descriptions.Item>
          <Descriptions.Item label="合同状态">{report.contract.status}</Descriptions.Item>
          <Descriptions.Item label="风险数">{risks.length}</Descriptions.Item>
          <Descriptions.Item label="审批数">{report.approvals.length}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={`风险明细 (${risks.length})`} style={{ marginBottom: 16 }}>
        <RiskList risks={risks} />
      </Card>

      {report.approvals.length > 0 && (
        <Card title={`审批记录 (已批准 ${approvedCount} / 待审批 ${pendingCount})`} style={{ marginBottom: 16 }}>
          {report.approvals.map((a) => (
            <Card key={a.id} size="small" style={{ marginBottom: 8 }} bordered>
              <Descriptions column={2} size="small">
                <Descriptions.Item label="审批 ID">{a.id}</Descriptions.Item>
                <Descriptions.Item label="类型">{a.approvalType}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={a.status === 'APPROVED' ? 'green' : a.status === 'REJECTED' ? 'red' : 'orange'}>
                    {statusLabels[a.status] || a.status}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="审批意见">{a.comment || '-'}</Descriptions.Item>
              </Descriptions>
            </Card>
          ))}
        </Card>
      )}

      {steps.length > 0 && (
        <Card title="审查执行过程" style={{ marginBottom: 16 }}>
          <Timeline
            items={steps.map((s) => ({
              color: s.status === 'COMPLETED' ? 'green' : s.status === 'FAILED' ? 'red' : 'blue',
              children: (
                <>
                  <Typography.Text strong>{s.stepType}</Typography.Text>
                  <Typography.Text type="secondary" style={{ marginLeft: 12, fontSize: 12 }}>{s.status}</Typography.Text>
                </>
              ),
            }))}
          />
        </Card>
      )}

      <Card title="最终结论">
        <Alert
          type={review.status === 'FAILED' ? 'error' : review.overallRiskLevel === 'HIGH' ? 'error' : review.overallRiskLevel === 'MEDIUM' ? 'warning' : 'success'}
          message={report.finalConclusion}
          description="本报告由 LexiFlow Agent 基于合同内容、合规规则库和审批记录生成。"
          showIcon
        />
      </Card>
    </>
  );
}
