import { Button, Card, Descriptions, Progress, Spin, Statistic, Typography } from 'antd';
import { Activity, Gauge, RefreshCw, Server } from 'lucide-react';
import { useEffect, useState } from 'react';
import { getObservabilitySummary, type ObservabilitySummary } from '../../api/observability';

function formatBytes(value: number) {
  if (!value) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = value;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(1)} ${units[index]}`;
}

function formatUptime(ms: number) {
  const minutes = Math.floor(ms / 60000);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  if (days > 0) return `${days} 天 ${hours % 24} 小时`;
  if (hours > 0) return `${hours} 小时 ${minutes % 60} 分钟`;
  return `${minutes} 分钟`;
}

export default function ObservabilityPage() {
  const [summary, setSummary] = useState<ObservabilitySummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    refresh();
  }, []);

  function refresh() {
    setLoading(true);
    getObservabilitySummary()
      .then(setSummary)
      .finally(() => setLoading(false));
  }

  if (loading && !summary) return <Spin style={{ display: 'block', marginTop: 80 }} size="large" />;

  const memoryPercent = summary
    ? Math.round((summary.runtime.usedMemoryBytes / summary.runtime.maxMemoryBytes) * 100)
    : 0;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>系统可观测性</Typography.Title>
        <Button icon={<RefreshCw size={16} />} onClick={refresh} loading={loading}>刷新</Button>
      </div>

      {summary && (
        <>
          <section className="metric-grid">
            <Card>
              <Statistic title="运行状态" value={summary.status} prefix={<Server size={18} />} valueStyle={{ color: '#389e0d' }} />
            </Card>
            <Card>
              <Statistic title="审查任务" value={summary.counters.reviews || 0} prefix={<Activity size={18} />} />
            </Card>
            <Card>
              <Statistic title="待审批" value={summary.counters.pendingApprovals || 0} prefix={<Gauge size={18} />} />
            </Card>
          </section>

          <section className="main-grid">
            <Card title="业务计数">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="合同">{summary.counters.contracts || 0}</Descriptions.Item>
                <Descriptions.Item label="启用工具">{summary.counters.enabledTools || 0}</Descriptions.Item>
                <Descriptions.Item label="启用用户">{summary.counters.activeUsers || 0}</Descriptions.Item>
                <Descriptions.Item label="生成时间">{new Date(summary.generatedAt).toLocaleString()}</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card title="运行时">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="运行时长">{formatUptime(summary.runtime.uptimeMs)}</Descriptions.Item>
                <Descriptions.Item label="线程数">{summary.runtime.liveThreads?.toFixed(0) || '-'}</Descriptions.Item>
                <Descriptions.Item label="内存">
                  <Progress percent={memoryPercent} size="small" />
                  <Typography.Text type="secondary">
                    {formatBytes(summary.runtime.usedMemoryBytes)} / {formatBytes(summary.runtime.maxMemoryBytes)}
                  </Typography.Text>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </section>

          <section style={{ marginTop: 16 }}>
            <Card title="审查状态分布">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="已创建">{summary.reviewStatuses.created || 0}</Descriptions.Item>
                <Descriptions.Item label="等待审批">{summary.reviewStatuses.waitingApproval || 0}</Descriptions.Item>
                <Descriptions.Item label="已完成">{summary.reviewStatuses.completed || 0}</Descriptions.Item>
                <Descriptions.Item label="失败">{summary.reviewStatuses.failed || 0}</Descriptions.Item>
                <Descriptions.Item label="已取消">{summary.reviewStatuses.cancelled || 0}</Descriptions.Item>
              </Descriptions>
            </Card>
          </section>
        </>
      )}
    </>
  );
}
