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

export default function AgentTracePage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const reviewId = searchParams.get('reviewId');
  const [trace, setTrace] = useState<ReviewTrace | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      if (!reviewId) {
        setLoading(false);
        return;
      }
      try {
        setTrace(await getReviewTrace(reviewId));
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [reviewId]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} size="large" />;
  if (!trace) return <Empty description="未找到 Agent Trace" />;

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>
        Agent Trace - 合同 #{id}
      </Typography.Title>

      <section className="metric-grid" style={{ marginBottom: 16 }}>
        <Card><Statistic title="步骤数" value={trace.metrics.stepCount} /></Card>
        <Card><Statistic title="LLM 调用" value={trace.metrics.llmCallCount} /></Card>
        <Card><Statistic title="工具调用" value={trace.metrics.toolCallCount} /></Card>
      </section>

      <Card style={{ marginBottom: 16 }}>
        <Descriptions column={4} size="small">
          <Descriptions.Item label="审查 ID">{trace.review.id}</Descriptions.Item>
          <Descriptions.Item label="状态"><Tag>{trace.review.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="总 Token">{trace.metrics.totalTokens}</Descriptions.Item>
          <Descriptions.Item label="步骤耗时">{trace.metrics.totalLatencyMs}ms</Descriptions.Item>
        </Descriptions>
      </Card>

      <Tabs
        items={[
          {
            key: 'steps',
            label: '执行步骤',
            children: (
              <Timeline
                items={trace.steps.map((s) => ({
                  color: s.status === 'COMPLETED' ? 'green' : s.status === 'FAILED' ? 'red' : 'blue',
                  children: (
                    <Card size="small">
                      <Typography.Text strong>{s.stepType}</Typography.Text>
                      <Tag style={{ marginLeft: 8 }}>{s.status}</Tag>
                      <pre style={{ whiteSpace: 'pre-wrap', marginTop: 8, fontSize: 12 }}>{prettyJson(s.outputSummary)}</pre>
                    </Card>
                  ),
                }))}
              />
            ),
          },
          {
            key: 'transitions',
            label: '状态流转',
            children: (
              <Timeline
                items={trace.transitions.map((t) => ({
                  children: `${t.fromStatus || 'START'} -> ${t.toStatus}：${t.reason || '-'}`,
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
                        <Descriptions.Item label="Provider">{log.provider || '-'}</Descriptions.Item>
                        <Descriptions.Item label="Model">{log.modelName || '-'}</Descriptions.Item>
                        <Descriptions.Item label="Prompt">{log.promptVersion || '-'}</Descriptions.Item>
                        <Descriptions.Item label="Token">{log.totalTokens || 0}</Descriptions.Item>
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
                      <Tag color={log.success ? 'green' : 'red'} style={{ marginLeft: 8 }}>{log.success ? 'SUCCESS' : 'FAILED'}</Tag>
                      <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{prettyJson(log.result)}</pre>
                    </Card>
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'rag',
            label: 'RAG 结果',
            children: trace.retrievalLogs.length === 0 ? <Empty description="暂无 RAG 检索日志" /> : (
              <List
                dataSource={trace.retrievalLogs}
                renderItem={(log) => (
                  <List.Item>
                    <Card size="small" style={{ width: '100%' }}>
                      <Typography.Text strong>{log.queryText}</Typography.Text>
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
