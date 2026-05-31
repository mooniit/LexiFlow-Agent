import { Button, Card, Descriptions, Empty, Skeleton, Tag, Typography } from 'antd';
import { FileTextOutlined, NodeIndexOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { getContract, getOriginalText, type Contract } from '../../api/contract';
import {
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
  const [events, setEvents] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const c = await getContract(contractId);
        setContract(c);
        getOriginalText(contractId).then((r) => setOriginalText(r.text)).catch(() => setOriginalText('（无法加载原文）'));

        if (initialReviewId) {
          const r = await getReview(initialReviewId);
          setReview(r);
          const [stepList, riskList] = await Promise.all([
            getReviewSteps(r.id),
            getReviewRisks(r.id),
          ]);
          setSteps(stepList);
          setRisks(riskList);
        }
      } catch { /* handled */ } finally {
        setLoading(false);
      }
    }
    load();
  }, [contractId, initialReviewId]);

  useEffect(() => {
    if (!review || review.status === 'COMPLETED' || review.status === 'FAILED' || review.status === 'CANCELLED') return;

    const es = subscribeReviewEvents(review.id);
    es.onmessage = (e) => {
      setEvents((prev) => [...prev, e.data]);
      try {
        const data = JSON.parse(e.data);
        if (data.type === 'STATUS_CHANGE') {
          setReview((prev) => prev ? { ...prev, status: data.status } : prev);
        } else if (data.type === 'STEP_COMPLETE') {
          setSteps((prev) => [...prev, data.step]);
        } else if (data.type === 'REVIEW_COMPLETE') {
          setReview(data.review);
          es.close();
        }
      } catch { /* non-JSON */ }
    };
    es.onerror = () => es.close();
    return () => es.close();
  }, [review?.id, review?.status]);

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
            <Tag color={review.status === 'COMPLETED' ? 'green' : review.status === 'FAILED' ? 'red' : 'blue'}>
              {review.status}
            </Tag>
          )}
          {review?.status === 'COMPLETED' && (
            <Button
              type="primary"
              icon={<FileTextOutlined />}
              onClick={() => navigate(`/contracts/${contractId}/report?reviewId=${review.id}`)}
            >
              查看报告
            </Button>
          )}
          {review && (
            <Button
              icon={<NodeIndexOutlined />}
              onClick={() => navigate(`/contracts/${contractId}/trace?reviewId=${review.id}`)}
            >
              审查链路
            </Button>
          )}
        </div>
      </div>

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
          <ReviewTimeline review={review} steps={steps} sseEvents={events} />

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
