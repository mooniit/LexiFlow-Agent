import { Card, Descriptions, Empty, Skeleton, Steps, Tag, Timeline, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ClockCircleOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { getContract, type Contract } from '../../api/contract';
import { getReviewTrace, type AgentStep, type StateTransition } from '../../api/review';

const statusLabels: Record<string, string> = {
  CREATED: '已创建', PARSING: '解析中', EXTRACTING: '条款抽取',
  RETRIEVING_RULES: '规则检索', ANALYZING: '风险分析', WAITING_APPROVAL: '等待审批',
  GENERATING_REPORT: '生成报告', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消',
};

export default function AgentTracePage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const contractId = id || '';
  const reviewId = searchParams.get('reviewId');
  const navigate = useNavigate();

  const [contract, setContract] = useState<Contract | null>(null);
  const [trace, setTrace] = useState<{
    review: any;
    steps: AgentStep[];
    transitions: StateTransition[];
  } | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const c = await getContract(contractId);
        setContract(c);
        if (reviewId) {
          const t = await getReviewTrace(reviewId);
          setTrace(t);
        }
      } catch { /* ignore */ } finally {
        setLoading(false);
      }
    }
    load();
  }, [contractId, reviewId]);

  if (loading) {
    return (
      <>
        <Typography.Title level={4} style={{ marginBottom: 16 }}>Agent 审查链路追踪</Typography.Title>
        <Card><Skeleton active paragraph={{ rows: 10 }} /></Card>
      </>
    );
  }

  if (!trace) {
    return (
      <>
        <Typography.Title level={4} style={{ marginBottom: 16 }}>Agent 审查链路追踪</Typography.Title>
        <Empty description="暂无 Trace 数据，请先发起审查。" />
      </>
    );
  }

  const totalDuration = trace.steps.reduce((sum, s) => sum + (s.durationMs || 0), 0);
  const completedSteps = trace.steps.filter((s) => s.status === 'COMPLETED').length;

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>
        Agent 审查链路追踪 — {contract?.contractName || `合同 #${contractId}`}
      </Typography.Title>

      {/* Overview */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Descriptions column={5} size="small">
          <Descriptions.Item label="审查任务">#{trace.review?.id}</Descriptions.Item>
          <Descriptions.Item label="当前状态">
            <Tag color={trace.review?.status === 'COMPLETED' ? 'green' : 'blue'}>
              {statusLabels[trace.review?.status] || trace.review?.status}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="风险等级">
            <Tag color={trace.review?.overallRisk === 'HIGH' ? 'red' : trace.review?.overallRisk === 'MEDIUM' ? 'orange' : 'green'}>
              {trace.review?.overallRisk || '-'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="步骤数">{trace.steps.length}</Descriptions.Item>
          <Descriptions.Item label="总耗时">{totalDuration}ms</Descriptions.Item>
        </Descriptions>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {/* State Transition Flow */}
        <Card
          title={<><SwapOutlined /> 状态流转</>}
          size="small"
        >
          {trace.transitions.length === 0 ? (
            <Empty description="暂无状态变更记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <Timeline
              items={trace.transitions.map((t) => ({
                color: t.toStatus === 'FAILED' ? 'red' : t.toStatus === 'COMPLETED' ? 'green' : 'blue',
                children: (
                  <div>
                    <Typography.Text strong>
                      {statusLabels[t.fromStatus] || t.fromStatus}
                      <Typography.Text type="secondary"> → </Typography.Text>
                      {statusLabels[t.toStatus] || t.toStatus}
                    </Typography.Text>
                    {t.reason && (
                      <Typography.Paragraph type="secondary" style={{ fontSize: 12, margin: '2px 0 0' }}>
                        {t.reason}
                      </Typography.Paragraph>
                    )}
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      {new Date(t.createdAt).toLocaleString()}
                    </Typography.Text>
                  </div>
                ),
              }))}
            />
          )}
        </Card>

        {/* Step Execution Details */}
        <Card
          title={<><ClockCircleOutlined /> 步骤执行 ({completedSteps}/{trace.steps.length})</>}
          size="small"
        >
          {trace.steps.length === 0 ? (
            <Empty description="暂无步骤记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            trace.steps.map((step) => (
              <Card key={step.id} size="small" style={{ marginBottom: 8 }} bordered>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <Typography.Text strong>{step.stepType}</Typography.Text>
                    <br />
                    {step.inputSummary && (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        输入: {step.inputSummary.length > 80 ? step.inputSummary.slice(0, 80) + '...' : step.inputSummary}
                      </Typography.Text>
                    )}
                  </div>
                  <div style={{ textAlign: 'right', flexShrink: 0 }}>
                    <Tag
                      icon={step.status === 'COMPLETED' ? <CheckCircleOutlined /> : step.status === 'FAILED' ? <CloseCircleOutlined /> : <LoadingOutlined />}
                      color={step.status === 'COMPLETED' ? 'green' : step.status === 'FAILED' ? 'red' : 'blue'}
                    >
                      {step.status}
                    </Tag>
                    <br />
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {step.durationMs}ms
                    </Typography.Text>
                  </div>
                </div>
                {step.outputSummary && (
                  <Typography.Paragraph style={{ fontSize: 12, marginTop: 4, marginBottom: 0 }} type="secondary">
                    输出: {step.outputSummary.length > 100 ? step.outputSummary.slice(0, 100) + '...' : step.outputSummary}
                  </Typography.Paragraph>
                )}
              </Card>
            ))
          )}
        </Card>
      </div>
    </>
  );
}
