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
  if (!trace) return <Empty description="No Agent Trace found" />;

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>
        Agent Trace - Contract #{id}
      </Typography.Title>

      <section className="metric-grid" style={{ marginBottom: 16 }}>
        <Card><Statistic title="Steps" value={trace.metrics.stepCount} /></Card>
        <Card><Statistic title="LLM calls" value={trace.metrics.llmCallCount} /></Card>
        <Card><Statistic title="Tool calls" value={trace.metrics.toolCallCount} /></Card>
        <Card><Statistic title="RAG searches" value={trace.metrics.retrievalCount} /></Card>
      </section>

      <Card style={{ marginBottom: 16 }}>
        <Descriptions column={4} size="small">
          <Descriptions.Item label="Review ID">{trace.review.id}</Descriptions.Item>
          <Descriptions.Item label="Status"><Tag>{trace.review.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="Total tokens">{trace.metrics.totalTokens}</Descriptions.Item>
          <Descriptions.Item label="Latency">{trace.metrics.totalLatencyMs}ms</Descriptions.Item>
        </Descriptions>
      </Card>

      <Tabs
        items={[
          {
            key: 'steps',
            label: 'Steps',
            children: trace.steps.length === 0 ? <Empty description="No step records" /> : (
              <Timeline
                items={trace.steps.map((step) => ({
                  color: step.status === 'COMPLETED' ? 'green' : step.status === 'FAILED' ? 'red' : 'blue',
                  children: (
                    <Card size="small">
                      <Typography.Text strong>{step.stepType}</Typography.Text>
                      <Tag style={{ marginLeft: 8 }}>{step.status}</Tag>
                      <pre style={{ whiteSpace: 'pre-wrap', marginTop: 8, fontSize: 12 }}>{prettyJson(step.outputSummary)}</pre>
                    </Card>
                  ),
                }))}
              />
            ),
          },
          {
            key: 'transitions',
            label: 'State transitions',
            children: trace.transitions.length === 0 ? <Empty description="No state transitions" /> : (
              <Timeline
                items={trace.transitions.map((transition) => ({
                  children: `${transition.fromStatus || 'START'} -> ${transition.toStatus}: ${transition.reason || '-'}`,
                }))}
              />
            ),
          },
          {
            key: 'llm',
            label: 'LLM logs',
            children: trace.llmCalls.length === 0 ? <Empty description="No LLM logs" /> : (
              <List
                dataSource={trace.llmCalls}
                renderItem={(log) => (
                  <List.Item>
                    <Card size="small" style={{ width: '100%' }}>
                      <Descriptions column={4} size="small">
                        <Descriptions.Item label="Provider">{log.provider || '-'}</Descriptions.Item>
                        <Descriptions.Item label="Model">{log.modelName || '-'}</Descriptions.Item>
                        <Descriptions.Item label="Prompt">{log.promptVersion || '-'}</Descriptions.Item>
                        <Descriptions.Item label="Tokens">{log.totalTokens || 0}</Descriptions.Item>
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
            label: 'Tool logs',
            children: trace.toolCalls.length === 0 ? <Empty description="No tool logs" /> : (
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
            label: 'RAG logs',
            children: trace.retrievalLogs.length === 0 ? <Empty description="No RAG logs" /> : (
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
