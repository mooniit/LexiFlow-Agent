import { Card, Empty, Spin, Steps, Timeline, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import type { AgentStep, ContractReview } from '../api/review';

const statusStepMap: Record<string, number> = {
  CREATED: 0, PARSING: 1, EXTRACTING: 2, RETRIEVING_RULES: 3,
  ANALYZING: 4, WAITING_APPROVAL: 5, GENERATING_REPORT: 6, COMPLETED: 7,
};

const stepItems = [
  { title: '合同解析' },
  { title: '条款抽取' },
  { title: '规则检索' },
  { title: '风险分析' },
  { title: '等待审批' },
  { title: '生成报告' },
  { title: '完成' },
];

type Props = {
  review: ContractReview | null;
  steps: AgentStep[];
  sseEvents: string[];
};

export default function ReviewTimeline({ review, steps, sseEvents }: Props) {
  const currentStep = review ? statusStepMap[review.status] ?? 0 : 0;

  return (
    <>
      <Card title="审查进度" size="small" style={{ marginBottom: 12 }}>
        <Steps
          direction="vertical"
          size="small"
          current={currentStep}
          status={review?.status === 'FAILED' ? 'error' : 'process'}
          items={stepItems}
        />
        {review?.failureReason && (
          <Typography.Paragraph type="danger" style={{ marginTop: 8, fontSize: 13 }}>
            失败原因：{review.failureReason}
          </Typography.Paragraph>
        )}
      </Card>

      <Card title="Agent 执行记录" size="small" style={{ marginBottom: 12 }}>
        {steps.length === 0 ? (
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
                    {s.status}
                  </Typography.Text>
                </>
              ),
            }))}
          />
        )}
      </Card>

      {sseEvents.length > 0 && (
        <Card title="实时事件" size="small">
          {sseEvents.slice(-8).map((e, i) => (
            <Typography.Paragraph key={i} style={{ fontSize: 12, marginBottom: 4 }} ellipsis>
              {e}
            </Typography.Paragraph>
          ))}
          <Spin size="small" style={{ display: 'block', marginTop: 8 }} />
        </Card>
      )}
    </>
  );
}
