import {
  Button,
  Card,
  Empty,
  Input,
  List,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { SearchOutlined, LinkOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState } from 'react';
import { listKnowledgeBases, searchKnowledge, type RetrievedChunk } from '../../api/knowledge';

type HistoryItem = { question: string; chunks: RetrievedChunk[] };

export default function KnowledgeQAPage() {
  const [bases, setBases] = useState<{ label: string; value: string }[]>([]);
  const [question, setQuestion] = useState('');
  const [searching, setSearching] = useState(false);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    listKnowledgeBases()
      .then((list) => setBases(list.map((b) => ({ label: b.name, value: b.id }))))
      .catch(() => {});
  }, []);

  async function handleSearch() {
    if (!question.trim()) return;
    setSearching(true);
    try {
      const chunks = await searchKnowledge(question.trim());
      setHistory((prev) => [{ question: question.trim(), chunks }, ...prev]);
      setQuestion('');
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 100);
    } catch (err: any) {
      // ignore
    } finally {
      setSearching(false);
    }
  }

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>合规知识库问答</Typography.Title>

      <Card style={{ marginBottom: 16 }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          基于公司合规规则库检索，回答内容引用真实规则来源，不编造规则。
        </Typography.Paragraph>
        <Space.Compact style={{ width: '100%' }}>
          <Input
            size="large"
            placeholder="输入合规问题，例如：公司对付款周期有什么要求？"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onPressEnter={handleSearch}
          />
          <Button type="primary" size="large" icon={<SearchOutlined />} loading={searching} onClick={handleSearch}>
            检索
          </Button>
        </Space.Compact>
      </Card>

      {history.length === 0 && !searching && (
        <Empty description="输入问题，开始合规知识检索" />
      )}

      {history.map((item, i) => (
        <Card key={i} style={{ marginBottom: 16 }}>
          <Typography.Title level={5} style={{ marginBottom: 12 }}>
            问题：{item.question}
          </Typography.Title>

          {item.chunks.length === 0 ? (
            <Typography.Text type="secondary">未找到相关规则内容。</Typography.Text>
          ) : (
            item.chunks.map((chunk, j) => (
              <Card key={j} size="small" style={{ marginBottom: 8 }} bordered>
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 8 }}>
                  {chunk.content}
                </Typography.Paragraph>
                <Space>
                  <Tag>{`相似度: ${(chunk.score * 100).toFixed(0)}%`}</Tag>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    <LinkOutlined /> 文档 #{chunk.documentId} · Chunk #{chunk.chunkId}
                  </Typography.Text>
                </Space>
              </Card>
            ))
          )}
        </Card>
      ))}

      {searching && <Spin style={{ display: 'block', margin: '20px auto' }} />}
      <div ref={bottomRef} />
    </>
  );
}
