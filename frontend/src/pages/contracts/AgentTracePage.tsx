import { Card, Descriptions, Empty, List, Spin, Statistic, Tabs, Tag, Timeline, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { getReviewTrace, type ReviewTrace } from '../../api/review';

function prettyJson(value?: string) {
  if (!value) return '-';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatMs(value?: number | string) {
  const numericValue = typeof value === 'string' ? Number(value) : value;
  if (numericValue === undefined || numericValue === null || !Number.isFinite(numericValue)) return '-';
  if (numericValue < 1000) return `${Math.round(numericValue)}ms`;
  return `${(numericValue / 1000).toFixed(numericValue < 10000 ? 1 : 0)}s`;
}

function formatTime(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('zh-CN', { hour12: false });
}

function stepDuration(startedAt?: string, finishedAt?: string) {
  if (!startedAt || !finishedAt) return undefined;
  const start = new Date(startedAt).getTime();
  const finish = new Date(finishedAt).getTime();
  if (Number.isNaN(start) || Number.isNaN(finish)) return undefined;
  return Math.max(0, finish - start);
}

export default function AgentTracePage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const reviewId = searchParams.get('reviewId');
  const [trace, setTrace] = useState<ReviewTrace | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      if (!reviewId) { setLoading(false); return; }
      try { setTrace(await getReviewTrace(reviewId)); } finally { setLoading(false); }
    }
    load();
  }, [reviewId]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} size="large" />;
  if (!trace) return <Empty description="暂无审查链路数据" />;

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>
        审查链路追踪 — 合同 #{id}
      </Typography.Title>

      <section className="metric-grid" style={{ marginBottom: 16 }}>
        <Card><Statistic title="执行步骤" value={trace.metrics.stepCount} /></Card>
        <Card><Statistic title="LLM 调用" value={trace.metrics.llmCallCount} /></Card>
        <Card><Statistic title="工具调用" value={trace.metrics.toolCallCount} /></Card>
        <Card><Statistic title="RAG 检索" value={trace.metrics.retrievalCount} /></Card>
        <Card><Statistic title="端到端耗时" value={formatMs(trace.metrics.wallClockMs)} /></Card>
        <Card><Statistic title="步骤执行耗时" value={formatMs(trace.metrics.stepDurationMs)} /></Card>
        <Card><Statistic title="工具耗时" value={formatMs(trace.metrics.toolLatencyMs)} /></Card>
        <Card><Statistic title="LLM 耗时" value={formatMs(trace.metrics.llmLatencyMs)} /></Card>
        <Card><Statistic title="RAG 耗时" value={formatMs(trace.metrics.retrievalLatencyMs)} /></Card>
      </section>

      <Card style={{ marginBottom: 16 }}>
        <Descriptions column={4} size="small">
          <Descriptions.Item label="审查 ID">{trace.review.id}</Descriptions.Item>
          <Descriptions.Item label="状态"><Tag>{trace.review.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="Token 消耗">{trace.metrics.totalTokens}</Descriptions.Item>
          <Descriptions.Item label="端到端耗时">{formatMs(trace.metrics.wallClockMs)}</Descriptions.Item>
          <Descriptions.Item label="工具耗时">{formatMs(trace.metrics.toolLatencyMs)}</Descriptions.Item>
          <Descriptions.Item label="LLM 耗时">{formatMs(trace.metrics.llmLatencyMs)}</Descriptions.Item>
          <Descriptions.Item label="RAG 耗时">{formatMs(trace.metrics.retrievalLatencyMs)}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Tabs
        items={[
          {
            key: 'steps',
            label: '执行步骤',
            children: trace.steps.length === 0 ? <Empty description="暂无步骤记录" /> : (
              <Timeline
                items={trace.steps.map((step) => ({
                  color: step.status === 'COMPLETED' ? 'green' : step.status === 'FAILED' ? 'red' : 'blue',
                  children: (
                    <Card size="small">
                      <Typography.Text strong>{step.stepType}</Typography.Text>
                      <Tag style={{ marginLeft: 8 }}>{step.status}</Tag>
                      <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                        {formatTime(step.startedAt)} - {formatTime(step.finishedAt)}
                        <span style={{ marginLeft: 8 }}>耗时 {formatMs(stepDuration(step.startedAt, step.finishedAt))}</span>
                      </Typography.Text>
                      <pre style={{ whiteSpace: 'pre-wrap', marginTop: 8, fontSize: 12 }}>{prettyJson(step.outputSummary)}</pre>
                    </Card>
                  ),
                }))}
              />
            ),
          },
          {
            key: 'transitions',
            label: '状态流转',
            children: trace.transitions.length === 0 ? <Empty description="暂无状态流转" /> : (
              <Timeline
                items={trace.transitions.map((t) => ({
                  children: `${t.fromStatus || 'START'} → ${t.toStatus}: ${t.reason || '-'}`,
                }))}
              />
            ),
          },
          {
            key: 'llm',
            label: 'LLM 日志',
            children: trace.llmCalls.length === 0 ? <Empty description="暂无 LLM 日志" /> : (
              <List
                dataSource={trace.llmCalls}
                renderItem={(log) => (
                  <List.Item>
                    <Card size="small" style={{ width: '100%' }}>
                      <Descriptions column={4} size="small">
                        <Descriptions.Item label="供应商">{log.provider || '-'}</Descriptions.Item>
                        <Descriptions.Item label="模型">{log.modelName || '-'}</Descriptions.Item>
                        <Descriptions.Item label="模板">{log.promptVersion || '-'}</Descriptions.Item>
                        <Descriptions.Item label="Token">{log.totalTokens || 0}</Descriptions.Item>
                        <Descriptions.Item label="耗时">{formatMs(log.latencyMs)}</Descriptions.Item>
                      </Descriptions>
                      <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{prettyJson(log.responseBody)}</pre>
                    </Card>
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'tools',
            label: '工具日志',
            children: trace.toolCalls.length === 0 ? <Empty description="暂无工具日志" /> : (
              <List
                dataSource={trace.toolCalls}
                renderItem={(log) => (
                  <List.Item>
                    <Card size="small" style={{ width: '100%' }}>
                      <Typography.Text strong>{log.toolName}</Typography.Text>
                      <Tag color={log.success ? 'green' : 'red'} style={{ marginLeft: 8 }}>{log.success ? '成功' : '失败'}</Tag>
                      <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                        耗时 {formatMs(log.latencyMs)}
                      </Typography.Text>
                      <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{prettyJson(log.result)}</pre>
                    </Card>
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'rag',
            label: 'RAG 日志',
            children: trace.retrievalLogs.length === 0 ? <Empty description="暂无 RAG 日志" /> : (
              <List
                dataSource={trace.retrievalLogs}
                renderItem={(log) => (
                  <List.Item>
                    <Card size="small" style={{ width: '100%' }}>
                      <Typography.Text strong>{log.queryText}</Typography.Text>
                      <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                        耗时 {formatMs(log.latencyMs)}
                      </Typography.Text>
                      <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{prettyJson(log.retrievedChunks)}</pre>
                    </Card>
                  </List.Item>
                )}
              />
            ),
          },
        ]}
      />
    </>
  );
}
