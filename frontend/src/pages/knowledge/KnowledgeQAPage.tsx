import {
  Button,
  Card,
  Input,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd';
import { SearchOutlined, UserOutlined, RobotOutlined, LinkOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState } from 'react';
import { searchKnowledge, type RetrievedChunk } from '../../api/knowledge';

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
};

export default function KnowledgeQAPage() {
  const [question, setQuestion] = useState('');
  const [searching, setSearching] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const chatRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<any>(null);

  useEffect(() => { inputRef.current?.focus(); }, []);

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
      const chunks = await searchKnowledge(query);
      const aiMsg: Message = {
        role: 'assistant',
        content: chunks.length === 0 ? '未找到相关规则内容，请尝试其他问题或咨询法务部门。' : '',
        chunks: chunks.length > 0 ? chunks : undefined,
      };
      setMessages((prev) => [...prev, aiMsg]);
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
      <div ref={chatRef} style={{ flex: 1, overflowY: 'auto', paddingRight: 4, marginBottom: 12 }}>
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
                  {!msg.chunks ? (
                    <Typography.Text type="secondary">{msg.content}</Typography.Text>
                  ) : (
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
