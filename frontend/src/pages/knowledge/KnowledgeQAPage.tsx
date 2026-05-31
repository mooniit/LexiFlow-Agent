import {
  Button,
  Card,
  Input,
  Skeleton,
  Space,
  Tag,
  Typography,
  List,
} from 'antd';
import { SearchOutlined, UserOutlined, RobotOutlined, LinkOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState } from 'react';
import { askKnowledge, listQaHistory, submitQaFeedback, type QaHistory, type RetrievedChunk } from '../../api/knowledge';

const EXAMPLE_QUESTIONS = [
  '公司对付款周期有什么要求？',
  '什么情况下需要法务主管审批？',
  '销售合同中数据保护条款必须包含哪些内容？',
  '客户要求无限赔偿责任时应该怎么处理？',
];

type Message = {
  role: 'user' | 'assistant';
  content: string;
  chunks?: RetrievedChunk[];
  historyId?: string;
};

export default function KnowledgeQAPage() {
  const [question, setQuestion] = useState('');
  const [searching, setSearching] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [history, setHistory] = useState<QaHistory[]>([]);
  const chatRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<any>(null);

  useEffect(() => { inputRef.current?.focus(); }, []);
  useEffect(() => { listQaHistory().then(setHistory).catch(() => setHistory([])); }, []);

  useEffect(() => {
    chatRef.current?.scrollTo({ top: chatRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  async function handleSearch(q?: string) {
    const query = (q || question).trim();
    if (!query || searching) return;
    const userMsg: Message = { role: 'user', content: query };
    setMessages((prev) => [...prev, userMsg]);
    setQuestion('');
    setSearching(true);
    try {
      const answer = await askKnowledge(query);
      const aiMsg: Message = {
        role: 'assistant',
        content: answer.answer,
        chunks: answer.references.length > 0 ? answer.references : undefined,
        historyId: answer.id,
      };
      setMessages((prev) => [...prev, aiMsg]);
      listQaHistory().then(setHistory).catch(() => undefined);
    } catch {
      const errMsg: Message = { role: 'assistant', content: '检索失败，请稍后重试。' };
      setMessages((prev) => [...prev, errMsg]);
    } finally {
      setSearching(false);
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 64px - 56px)' }}>
      <Typography.Title level={4} style={{ marginBottom: 12 }}>合规知识库问答</Typography.Title>

      {/* Chat area */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 280px', gap: 16, minHeight: 0, flex: 1 }}>
      <div ref={chatRef} style={{ overflowY: 'auto', paddingRight: 4, marginBottom: 12 }}>
        {messages.length === 0 ? (
          <div style={{ textAlign: 'center', paddingTop: 60 }}>
            <RobotOutlined style={{ fontSize: 48, color: '#b0bec5', marginBottom: 16 }} />
            <Typography.Title level={5} type="secondary">合规知识库助手</Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 20 }}>
              基于公司合规规则库检索，不编造规则
            </Typography.Paragraph>
            <Space wrap style={{ justifyContent: 'center' }}>
              {EXAMPLE_QUESTIONS.map((q) => (
                <Tag
                  key={q}
                  style={{ cursor: 'pointer', padding: '6px 14px', fontSize: 14, borderRadius: 16 }}
                  onClick={() => handleSearch(q)}
                >
                  {q}
                </Tag>
              ))}
            </Space>
          </div>
        ) : (
          messages.map((msg, i) => (
            <div key={i} style={{ marginBottom: 20 }}>
              {/* User message: right-aligned bubble */}
              {msg.role === 'user' && (
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <div style={{ maxWidth: '75%', padding: '12px 16px', borderRadius: '12px 12px 4px 12px', background: '#e3f2fd' }}>
                    <Typography.Text style={{ fontSize: 15 }}>{msg.content}</Typography.Text>
                  </div>
                  <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#e8eaf6', display: 'flex', alignItems: 'center', justifyContent: 'center', marginLeft: 10, flexShrink: 0 }}>
                    <UserOutlined style={{ color: '#3949ab', fontSize: 18 }} />
                  </div>
                </div>
              )}

              {/* Assistant: chunk cards directly shown */}
              {msg.role === 'assistant' && (
                <div>
                  <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 15 }}>
                    {msg.content}
                  </Typography.Paragraph>
                  {msg.historyId && (
                    <Space style={{ marginBottom: 8 }}>
                      <Button size="small" onClick={() => submitQaFeedback(msg.historyId!, 'HELPFUL')}>有帮助</Button>
                      <Button size="small" onClick={() => submitQaFeedback(msg.historyId!, 'NOT_HELPFUL')}>不准确</Button>
                    </Space>
                  )}
                  {msg.chunks && (
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                        <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#e3f2fd', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <RobotOutlined style={{ color: '#1565c0', fontSize: 15 }} />
                        </div>
                        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                          检索到 {msg.chunks.length} 条相关规则
                        </Typography.Text>
                      </div>
                      {msg.chunks.map((chunk, j) => (
                        <Card key={j} size="small" style={{ marginBottom: 8, borderLeft: '3px solid #90a4ae' }}>
                          <Typography.Paragraph style={{ fontSize: 15, whiteSpace: 'pre-wrap', marginBottom: 6 }}>
                            {chunk.content}
                          </Typography.Paragraph>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            <LinkOutlined /> 文档 #{chunk.documentId} · 相似度 {(chunk.score * 100).toFixed(0)}%
                          </Typography.Text>
                        </Card>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))
        )}

        {searching && (
          <div style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#e3f2fd', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <RobotOutlined style={{ color: '#1565c0', fontSize: 15 }} />
              </div>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>检索中...</Typography.Text>
            </div>
            <Skeleton active paragraph={{ rows: 3 }} />
          </div>
        )}
      </div>
      <Card title="问答历史" size="small" style={{ overflow: 'hidden' }}>
        <List
          size="small"
          dataSource={history}
          locale={{ emptyText: '暂无历史' }}
          renderItem={(item) => (
            <List.Item style={{ cursor: 'pointer' }} onClick={() => handleSearch(item.question)}>
              <List.Item.Meta
                title={<Typography.Text ellipsis>{item.question}</Typography.Text>}
                description={<Typography.Text type="secondary" ellipsis>{item.answer}</Typography.Text>}
              />
            </List.Item>
          )}
        />
      </Card>
      </div>

      {/* Input bar */}
      <div style={{ flexShrink: 0, borderTop: '1px solid #e8e8e8', paddingTop: 12 }}>
        <Space.Compact style={{ width: '100%' }}>
          <Input
            ref={inputRef}
            size="large"
            placeholder="输入合规问题，或点击上方示例"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onPressEnter={() => handleSearch()}
            disabled={searching}
          />
          <Button type="primary" size="large" icon={<SearchOutlined />} loading={searching} onClick={() => handleSearch()}>
            发送
          </Button>
        </Space.Compact>
      </div>
    </div>
  );
}
