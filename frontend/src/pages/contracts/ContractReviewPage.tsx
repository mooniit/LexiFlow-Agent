import { Alert, Button, Card, Descriptions, Skeleton, Tag, Typography } from 'antd';
import { BranchesOutlined, FileTextOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { getContract, getOriginalText, type Contract } from '../../api/contract';
import {
  createReview,
  getReview,
  getReviewRisks,
  getReviewSteps,
  subscribeReviewEvents,
  type ContractReview,
  type AgentStep,
  type ClauseRisk,
} from '../../api/review';
import RiskList from '../../components/RiskList';
import ReviewTimeline from '../../components/ReviewTimeline';

export default function ContractReviewPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const contractId = id || '';
  const initialReviewId = searchParams.get('reviewId');
  const navigate = useNavigate();

  const [contract, setContract] = useState<Contract | null>(null);
  const [originalText, setOriginalText] = useState('');
  const [review, setReview] = useState<ContractReview | null>(null);
  const [steps, setSteps] = useState<AgentStep[]>([]);
  const [risks, setRisks] = useState<ClauseRisk[]>([]);
  const [loading, setLoading] = useState(true);

  const refreshReviewData = useCallback(async (reviewId: string) => {
    try {
      const [r, stepList, riskList] = await Promise.all([
        getReview(reviewId),
        getReviewSteps(reviewId),
        getReviewRisks(reviewId),
      ]);
      setReview(r);
      setSteps(stepList);
      setRisks(riskList);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => {
    async function load() {
      try {
        const c = await getContract(contractId);
        setContract(c);
        getOriginalText(contractId).then((r) => setOriginalText(r.text)).catch(() => setOriginalText('（无法加载原文）'));
        if (initialReviewId) {
          await refreshReviewData(initialReviewId);
        }
      } catch { /* handled */ } finally {
        setLoading(false);
      }
    }
    load();
  }, [contractId, initialReviewId, refreshReviewData]);

  useEffect(() => {
    if (!review || review.status === 'COMPLETED' || review.status === 'FAILED' || review.status === 'CANCELLED') return;

    const es = subscribeReviewEvents(review.id);
    es.onmessage = () => { refreshReviewData(review.id); };
    es.onerror = () => es.close();
    return () => es.close();
  }, [review?.id, review?.status, refreshReviewData]);

  function statusLabel(s: string) {
    const labels: Record<string, string> = {
      CREATED: '已创建', PARSING: '解析中', EXTRACTING: '条款抽取',
      RETRIEVING_RULES: '规则检索', ANALYZING: '风险分析', WAITING_APPROVAL: '等待审批',
      GENERATING_REPORT: '生成报告', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消',
    };
    return labels[s] || s;
  }

  if (loading) {
    return (
      <>
        <Typography.Title level={4} style={{ marginBottom: 16 }}>合同审查详情</Typography.Title>
        <div className="review-layout">
          <Card><Skeleton active paragraph={{ rows: 12 }} /></Card>
          <div className="review-center">
            <Card><Skeleton active paragraph={{ rows: 8 }} /></Card>
          </div>
          <div className="review-right">
            <Card><Skeleton active paragraph={{ rows: 6 }} /></Card>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {contract?.contractName || `合同 #${contractId}`}
        </Typography.Title>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {review && (
            <Tag color={review.status === 'COMPLETED' ? 'green' : review.status === 'FAILED' ? 'red' : review.status === 'WAITING_APPROVAL' ? 'orange' : 'blue'}>
              {statusLabel(review.status)}
            </Tag>
          )}
          {review ? (
            <>
              {review.status === 'WAITING_APPROVAL' && (
                <Button icon={<ExclamationCircleOutlined />} onClick={() => navigate('/approvals')}>
                  需要审批
                </Button>
              )}
              {review.status === 'COMPLETED' && (
                <Button type="primary" icon={<FileTextOutlined />} onClick={() => navigate(`/contracts/${contractId}/report?reviewId=${review.id}`)}>
                  查看报告
                </Button>
              )}
              <Button icon={<BranchesOutlined />} onClick={() => navigate(`/contracts/${contractId}/trace?reviewId=${review.id}`)}>
                审查链路
              </Button>
            </>
          ) : (
            <Button type="primary" icon={<BranchesOutlined />} onClick={async () => {
              try {
                const r = await createReview(contractId);
                navigate(`/contracts/${contractId}?reviewId=${r.id}`, { replace: true });
                window.location.reload();
              } catch { /* ignore */ }
            }}>
              发起审查
            </Button>
          )}
        </div>
      </div>

      {review?.status === 'FAILED' && (
        <Alert
          type="error"
          showIcon
          message="审查失败"
          description={review.failureReason || '未知错误，请重新发起审查。'}
          style={{ marginBottom: 16 }}
        />
      )}

      <div className="review-layout">
        {/* Left: Original contract text */}
        <Card title="合同原文" className="review-left">
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.7 }}>
            {originalText || '暂无原文'}
          </pre>
        </Card>

        {/* Middle: Risk points */}
        <div className="review-center">
          <Card title={`风险点 (${risks.length})`}>
            <RiskList risks={risks} />
          </Card>
        </div>

        {/* Right: Agent Timeline + SSE */}
        <div className="review-right">
          <ReviewTimeline review={review} steps={steps} sseEvents={[]} />

          {contract && (
            <Card title="合同信息" size="small" style={{ marginTop: 12 }}>
              <Descriptions column={1} size="small">
                <Descriptions.Item label="类型">{contract.contractType || '-'}</Descriptions.Item>
                <Descriptions.Item label="客户">{contract.customerName || '-'}</Descriptions.Item>
                <Descriptions.Item label="金额">
                  {contract.contractAmount ? `¥${contract.contractAmount.toLocaleString()}` : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="状态">{contract.status}</Descriptions.Item>
              </Descriptions>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}
