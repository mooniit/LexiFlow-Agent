import { Alert, Card, Descriptions, Empty, Spin, Tag, Timeline, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { getContract, type Contract } from '../../api/contract';
import { getReview, getReviewRisks, getReviewSteps, type ContractReview, type ClauseRisk, type AgentStep } from '../../api/review';
import { listApprovals, getApprovalHistory, type ApprovalRequest, type ApprovalHistory } from '../../api/approval';
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
  const contractId = Number(id);
  const reviewId = searchParams.get('reviewId');

  const [contract, setContract] = useState<Contract | null>(null);
  const [review, setReview] = useState<ContractReview | null>(null);
  const [risks, setRisks] = useState<ClauseRisk[]>([]);
  const [steps, setSteps] = useState<AgentStep[]>([]);
  const [approvals, setApprovals] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const c = await getContract(contractId);
        setContract(c);
        if (reviewId) {
          const r = await getReview(Number(reviewId));
          setReview(r);
          const [riskList, stepList, approvalList] = await Promise.all([
            getReviewRisks(r.id),
            getReviewSteps(r.id),
            listApprovals(undefined, r.id),
          ]);
          setRisks(riskList);
          setSteps(stepList);
          setApprovals(approvalList);
        }
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [contractId, reviewId]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} size="large" />;

  const highRisks = risks.filter((r) => r.riskLevel === 'HIGH').length;
  const mediumRisks = risks.filter((r) => r.riskLevel === 'MEDIUM').length;
  const lowRisks = risks.filter((r) => r.riskLevel === 'LOW').length;
  const approvedCount = approvals.filter((a) => a.status === 'APPROVED').length;
  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>
        审查报告 — {contract?.contractName || `合同 #${contractId}`}
      </Typography.Title>

      {/* Risk Summary Banner */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 16 }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            总体风险等级：
          </Typography.Title>
          <Tag color={riskColor[review?.overallRisk || '']} style={{ fontSize: 20, padding: '4px 20px' }}>
            {riskLabel[review?.overallRisk || ''] || review?.overallRisk || '未知'}
          </Tag>
        </div>
        <Descriptions column={4} size="small">
          <Descriptions.Item label="高风险"><Typography.Text strong type="danger">{highRisks}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="中风险"><Typography.Text strong type="warning">{mediumRisks}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="低风险"><Typography.Text strong type="success">{lowRisks}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="审查状态"><Tag>{review?.status}</Tag></Descriptions.Item>
        </Descriptions>
      </Card>

      {/* Risk Details */}
      <Card title={`风险明细 (${risks.length})`} style={{ marginBottom: 16 }}>
        <RiskList risks={risks} />
      </Card>

      {/* Approval Records */}
      {approvals.length > 0 && (
        <Card title={`审批记录 (已批准 ${approvedCount} / 待审批 ${pendingCount})`} style={{ marginBottom: 16 }}>
          {approvals.map((a) => (
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

      {/* Agent Execution Timeline */}
      {steps.length > 0 && (
        <Card title="审查执行过程" style={{ marginBottom: 16 }}>
          <Timeline
            items={steps.map((s) => ({
              color: s.status === 'COMPLETED' ? 'green' : s.status === 'FAILED' ? 'red' : 'blue',
              children: (
                <>
                  <Typography.Text strong>{s.stepType}</Typography.Text>
                  <Typography.Text type="secondary" style={{ marginLeft: 12, fontSize: 12 }}>
                    {s.status} · {s.durationMs}ms
                  </Typography.Text>
                </>
              ),
            }))}
          />
        </Card>
      )}

      {/* Final Conclusion */}
      <Card title="最终结论">
        {review?.status === 'COMPLETED' ? (
          <Alert
            type={review.overallRisk === 'HIGH' ? 'error' : review.overallRisk === 'MEDIUM' ? 'warning' : 'success'}
            message={
              review.overallRisk === 'HIGH'
                ? '审查发现高风险条款，建议法务部门审核后再签署。'
                : review.overallRisk === 'MEDIUM'
                  ? '审查发现中等风险条款，建议合同经办人关注并修改。'
                  : '审查未发现重大风险，合同可正常签署。'
            }
            description={
              <>
                <p>本报告由 LexiFlow Agent 自动生成，审查依据为公司合规规则库。</p>
                <p>如对审查结果有疑问，请联系法务部门进行人工复核。</p>
              </>
            }
            showIcon
          />
        ) : review?.status === 'FAILED' ? (
          <Alert type="error" message="审查过程异常" description={review.failReason || '未知错误'} showIcon />
        ) : (
          <Alert type="info" message={`审查任务状态：${review?.status || '未知'}，报告尚未最终生成。`} showIcon />
        )}
      </Card>
    </>
  );
}
