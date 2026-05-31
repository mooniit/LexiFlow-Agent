import { Card, Tag, Typography, Empty } from 'antd';
import { AlertOutlined } from '@ant-design/icons';
import type { ClauseRisk } from '../api/review';

const riskColor: Record<string, string> = { LOW: 'green', MEDIUM: 'orange', HIGH: 'red' };
const riskLabel: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' };

function formatEvidence(evidenceRules?: string): string[] {
  if (!evidenceRules) return [];
  try {
    const parsed = JSON.parse(evidenceRules);
    const refs = parsed.references || [];
    return refs.filter((r: any) => r.content).map((r: any) => r.content);
  } catch {
    return [];
  }
}

const riskTypeLabels: Record<string, string> = {
  PAYMENT_TERM_TOO_LONG: '付款周期过长',
  UNLIMITED_LIABILITY: '无限责任风险',
  MISSING_DATA_PROTECTION: '缺失数据保护条款',
  NON_STANDARD_JURISDICTION: '非标准争议解决',
  AUTO_RENEWAL_RISK: '自动续约风险',
  AUTO_RENEWAL_WITHOUT_NOTICE: '自动续约无通知',
  ONE_SIDED_TERMINATION: '单方解除权',
  CONFIDENTIALITY_WEAK: '保密条款薄弱',
  IP_OWNERSHIP_UNCLEAR: '知识产权归属不明',
  HIGH_CONTRACT_AMOUNT: '合同金额过高',
  MISSING_TERMINATION_CLAUSE: '缺失终止条款',
};

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
        <Card key={r.id} size="small" className={`risk-card risk-card-${r.riskLevel.toLowerCase()}`} style={{ marginBottom: 12 }} bordered loading={loading}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Tag color={riskColor[r.riskLevel] || 'default'}>{riskLabel[r.riskLevel] || r.riskLevel}</Tag>
            <Typography.Text strong>{r.clauseName || riskTypeLabels[r.riskType] || r.riskType}</Typography.Text>
            {r.requiresApproval && <Tag color="red" icon={<AlertOutlined />}>需审批</Tag>}
          </div>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 4 }}>
            <strong>风险原因：</strong>{r.reason}
          </Typography.Paragraph>
          <Typography.Paragraph style={{ marginBottom: 4 }}>
            <strong>修改建议：</strong>{r.suggestion}
          </Typography.Paragraph>
          {r.evidenceRules && (() => {
            const contents = formatEvidence(r.evidenceRules);
            return contents.length > 0 ? (
              <div style={{ marginTop: 8 }}>
                <Typography.Text strong style={{ fontSize: 13 }}>引用依据：</Typography.Text>
                {contents.map((c, i) => (
                  <Typography.Paragraph key={i} type="secondary" style={{ fontSize: 13, marginBottom: 4, whiteSpace: 'pre-wrap', borderLeft: '2px solid #d9d9d9', paddingLeft: 10 }}>
                    {c}
                  </Typography.Paragraph>
                ))}
              </div>
            ) : null;
          })()}
        </Card>
      ))}
    </>
  );
}
