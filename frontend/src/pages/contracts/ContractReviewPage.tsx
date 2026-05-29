import {
  Alert,
  Card,
  Descriptions,
  Empty,
  Spin,
  Steps,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import {
  AlertOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { getContract, getOriginalText, getClauses, type Contract, type ContractClause } from '../../api/contract';
import {
  getReview,
  getReviewRisks,
  getReviewSteps,
  subscribeReviewEvents,
  type ContractReview,
  type AgentStep,
  type ClauseRisk,
} from '../../api/review';

const riskColor: Record<string, string> = { LOW: 'green', MEDIUM: 'orange', HIGH: 'red' };
const riskLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高' };

export default function ContractReviewPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const contractId = Number(id);
  const initialReviewId = searchParams.get('reviewId');

  const [contract, setContract] = useState<Contract | null>(null);
  const [originalText, setOriginalText] = useState('');
  const [clauses, setClauses] = useState<ContractClause[]>([]);
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

        const [text, clauseList] = await Promise.all([
          getOriginalText(contractId).then((r) => r.text).catch(() => '（无法加载原文）'),
          getClauses(contractId).catch(() => []),
        ]);
        setOriginalText(text);
        setClauses(clauseList);

        if (initialReviewId) {
          const r = await getReview(Number(initialReviewId));
          setReview(r);
          const [stepList, riskList] = await Promise.all([
            getReviewSteps(r.id),
            getReviewRisks(r.id),
          ]);
          setSteps(stepList);
          setRisks(riskList);
        }
      } catch {
        // handled
      } finally {
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
      } catch { /* non-JSON event, ignore */ }
    };
    es.onerror = () => es.close();
    return () => es.close();
  }, [review?.id, review?.status]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} size="large" />;

  const statusStep = {
    CREATED: 0, PARSING: 1, EXTRACTING: 2, RETRIEVING_RULES: 3,
    ANALYZING: 4, WAITING_APPROVAL: 5, GENERATING_REPORT: 6, COMPLETED: 7,
  } as Record<string, number>;

  const currentStep = statusStep[review?.status || ''] ?? 0;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {contract?.contractName || `合同 #${contractId}`}
        </Typography.Title>
        {review && (
          <Tag color={review.status === 'COMPLETED' ? 'green' : review.status === 'FAILED' ? 'red' : 'blue'}>
            {review.status}
          </Tag>
        )}
      </div>

      <div className="review-layout">
        {/* Left: Original contract text */}
        <Card title="合同原文" className="review-left">
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.7 }}>
            {originalText || '暂无原文'}
          </pre>
        </Card>

        {/* Middle: Risk points & suggestions */}
        <div className="review-center">
          <Card title="审查进度" style={{ marginBottom: 16 }}>
            <Steps
              direction="vertical"
              size="small"
              current={currentStep}
              items={[
                { title: '合同解析' },
                { title: '条款抽取' },
                { title: '规则检索' },
                { title: '风险分析' },
                { title: '等待审批' },
                { title: '生成报告' },
                { title: '完成' },
              ]}
            />
            {review?.failReason && (
              <Alert message="失败原因" description={review.failReason} type="error" showIcon style={{ marginTop: 12 }} />
            )}
          </Card>

          <Card title={`风险点 (${risks.length})`}>
            {risks.length === 0 ? (
              <Empty description={review ? '暂未发现风险' : '暂无审查记录'} />
            ) : (
              risks.map((r) => (
                <Card key={r.id} size="small" style={{ marginBottom: 12 }} bordered>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <Tag color={riskColor[r.riskLevel]}>{riskLabel[r.riskLevel] || r.riskLevel}</Tag>
                    <Typography.Text strong>{r.clauseName || r.riskType}</Typography.Text>
                    {r.requiresApproval && <Tag color="red" icon={<AlertOutlined />}>需审批</Tag>}
                  </div>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 4 }}>
                    风险原因：{r.reason}
                  </Typography.Paragraph>
                  <Typography.Paragraph style={{ marginBottom: 4 }}>
                    修改建议：{r.suggestion}
                  </Typography.Paragraph>
                  {r.evidenceRules && (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      引用依据：{r.evidenceRules}
                    </Typography.Text>
                  )}
                </Card>
              ))
            )}
          </Card>
        </div>

        {/* Right: Agent Timeline & RAG */}
        <div className="review-right">
          <Card title="Agent 执行时间线" size="small">
            {steps.length === 0 && events.length === 0 ? (
              <Empty description="暂无执行记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <Timeline
                items={steps.map((s) => ({
                  dot:
                    s.status === 'COMPLETED' ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> :
                    s.status === 'FAILED' ? <CloseCircleOutlined style={{ color: '#ff4d4f' }} /> :
                    <LoadingOutlined />,
                  children: (
                    <>
                      <Typography.Text strong>{s.stepType}</Typography.Text>
                      <br />
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {s.status} · {s.durationMs}ms
                      </Typography.Text>
                    </>
                  ),
                }))}
              />
            )}
          </Card>

          {/* SSE real-time events */}
          {events.length > 0 && (
            <Card title="实时事件" size="small" style={{ marginTop: 12 }}>
              {events.slice(-5).map((e, i) => (
                <Typography.Paragraph key={i} style={{ fontSize: 12, marginBottom: 4 }} ellipsis>
                  {e}
                </Typography.Paragraph>
              ))}
            </Card>
          )}

          {/* Contract info */}
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
