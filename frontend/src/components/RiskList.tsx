import { Card, Tag, Typography, Empty } from 'antd';
import { AlertOutlined } from '@ant-design/icons';
import type { ClauseRisk } from '../api/review';

const riskColor: Record<string, string> = { LOW: 'green', MEDIUM: 'orange', HIGH: 'red' };
const riskLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高' };

type Props = {
  risks: ClauseRisk[];
  loading?: boolean;
};

export default function RiskList({ risks, loading }: Props) {
  if (!loading && risks.length === 0) {
    return <Empty description="暂未发现风险" />;
  }

  return (
    <>
      {risks.map((r) => (
        <Card key={r.id} size="small" style={{ marginBottom: 12 }} bordered loading={loading}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Tag color={riskColor[r.riskLevel] || 'default'}>{riskLabel[r.riskLevel] || r.riskLevel}</Tag>
            <Typography.Text strong>{r.clauseName || r.riskType}</Typography.Text>
            {r.requiresApproval && <Tag color="red" icon={<AlertOutlined />}>需审批</Tag>}
          </div>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 4 }}>
            <strong>风险原因：</strong>{r.reason}
          </Typography.Paragraph>
          <Typography.Paragraph style={{ marginBottom: 4 }}>
            <strong>修改建议：</strong>{r.suggestion}
          </Typography.Paragraph>
          {r.evidenceRules && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              <strong>引用依据：</strong>{r.evidenceRules}
            </Typography.Text>
          )}
        </Card>
      ))}
    </>
  );
}
