import { Card, Empty, List, Spin, Steps, Tag, Timeline, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import { useEffect, useState } from 'react';
import type { AgentStep, ClauseRisk, ContractReview, LlmCallLog, RetrievalLog, ReviewEvent } from '../api/review';

const statusStepMap: Record<string, number> = {
  CREATED: 0, PARSING: 0, EXTRACTING: 1, RETRIEVING_RULES: 2,
  ANALYZING: 3, WAITING_APPROVAL: 4, GENERATING_REPORT: 5, COMPLETED: 6,
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

const stepLabels: Record<string, string> = {
  CONTRACT_PARSE: '合同解析',
  CLAUSE_EXTRACTION: '条款抽取',
  RULE_RETRIEVAL: '规则检索',
  RISK_ANALYSIS: '风险分析',
  REPORT_GENERATION: '报告生成',
};

const eventLabels: Record<string, string> = {
  CONNECTED: '实时连接',
  CREATED: '任务创建',
  PARSING: '合同解析',
  EXTRACTING: '条款抽取',
  RETRIEVING_RULES: '规则检索',
  ANALYZING: '风险分析',
  WAITING_APPROVAL: '等待审批',
  GENERATING_REPORT: '生成报告',
  COMPLETED: '审查完成',
  FAILED: '审查失败',
  CLAUSE_EXTRACTION: '条款抽取',
  RULE_RETRIEVAL: '规则检索',
  RISK_ANALYSIS: '风险分析',
  CONTRACT_PARSE: '合同解析',
};

const statusLabels: Record<string, string> = {
  RUNNING: '进行中',
  COMPLETED: '已完成',
  FAILED: '失败',
};

const statusColors: Record<string, string> = {
  RUNNING: 'processing',
  COMPLETED: 'success',
  FAILED: 'error',
};

function parseJson<T>(value?: string): T | null {
  if (!value) return null;
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}

function summarizeRetrieved(log: RetrievalLog) {
  const chunks = parseJson<Array<{ score?: number; content?: string }>>(log.retrievedChunks) || [];
  return chunks.slice(0, 3).map((chunk) => ({
    score: chunk.score,
    content: chunk.content || '',
  }));
}

function formatMs(value: number) {
  if (!Number.isFinite(value) || value < 0) return '-';
  if (value < 1000) return `${Math.round(value)}ms`;
  return `${(value / 1000).toFixed(value < 10000 ? 1 : 0)}s`;
}

function formatTime(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

function stepDuration(step: AgentStep, now: number) {
  if (!step.startedAt) return 0;
  const start = new Date(step.startedAt).getTime();
  const end = step.finishedAt ? new Date(step.finishedAt).getTime() : now;
  if (Number.isNaN(start) || Number.isNaN(end)) return 0;
  return Math.max(0, end - start);
}

type Props = {
  review: ContractReview | null;
  steps: AgentStep[];
  sseEvents: ReviewEvent[];
  retrievalLogs?: RetrievalLog[];
  llmCalls?: LlmCallLog[];
  risks?: ClauseRisk[];
};

export default function ReviewTimeline({ review, steps, sseEvents, retrievalLogs = [], llmCalls = [], risks = [] }: Props) {
  const [now, setNow] = useState(Date.now());
  const currentStep = review ? statusStepMap[review.status] ?? 0 : 0;
  const latestRetrieval = retrievalLogs[retrievalLogs.length - 1];
  const latestReferences = latestRetrieval ? summarizeRetrieved(latestRetrieval) : [];
  const llmSuccessCount = llmCalls.filter((log) => log.success).length;
  const llmEnhancedRiskCount = risks.filter((risk) => parseJson<{ llmEnhanced?: boolean }>(risk.evidenceRules)?.llmEnhanced).length;
  const hasRunningStep = steps.some((step) => step.status === 'RUNNING');

  useEffect(() => {
    if (!hasRunningStep) return undefined;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [hasRunningStep]);

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
                  <Typography.Text strong>{stepLabels[s.stepType] || s.stepType}</Typography.Text>
                  <Tag color={statusColors[s.status] || 'default'} style={{ marginLeft: 8 }}>
                    {statusLabels[s.status] || s.status}
                  </Tag>
                  <br />
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {formatTime(s.startedAt)} - {formatTime(s.finishedAt)}
                    <span style={{ marginLeft: 8 }}>耗时 {formatMs(stepDuration(s, now))}</span>
                  </Typography.Text>
                  {s.errorMessage && (
                    <>
                      <br />
                      <Typography.Text type="danger" style={{ fontSize: 12 }}>
                        {s.errorMessage}
                      </Typography.Text>
                    </>
                  )}
                </>
              ),
            }))}
          />
        )}
      </Card>

      <Card title="Agent 依据" size="small" style={{ marginBottom: 12 }}>
        {latestRetrieval ? (
          <>
            <Typography.Paragraph style={{ marginBottom: 8 }}>
              当前检索：<Typography.Text strong>{latestRetrieval.queryText}</Typography.Text>
            </Typography.Paragraph>
            {latestReferences.length === 0 ? (
              <Empty description="暂无命中法条" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <List
                size="small"
                dataSource={latestReferences}
                renderItem={(item) => (
                  <List.Item>
                    <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                      <Tag color="blue">{item.score?.toFixed?.(4) || '-'}</Tag>
                      {item.content}
                    </Typography.Paragraph>
                  </List.Item>
                )}
              />
            )}
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              LLM 增强：{llmSuccessCount > 0 ? `已记录 ${llmSuccessCount} 次调用` : llmEnhancedRiskCount > 0 ? `已增强 ${llmEnhancedRiskCount} 个风险点` : '暂无记录或使用规则兜底'}
            </Typography.Text>
          </>
        ) : (
          <Empty description="暂无 RAG/LLM 依据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>

      {sseEvents.length > 0 && (
        <Card title="实时事件" size="small">
          {sseEvents.slice(-8).map((e, i) => (
            <Typography.Paragraph key={i} style={{ fontSize: 12, marginBottom: 4 }} ellipsis>
              <Tag>{eventLabels[e.type] || e.type}</Tag>
              {e.message}
            </Typography.Paragraph>
          ))}
          <Spin size="small" style={{ display: 'block', marginTop: 8 }} />
        </Card>
      )}
    </>
  );
}
