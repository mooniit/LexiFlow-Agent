import { Button, Card, Form, Input, Layout, List, Statistic, Tag, Timeline, Typography } from 'antd';
import { Activity, FileText, LogIn, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { clearToken, getToken, login } from './api/client';

const { Header, Content, Sider } = Layout;

type UserProfile = {
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
};

type LoginResponse = {
  token: string;
  user: UserProfile;
};

type ApiResponse<T> = {
  success: boolean;
  data: T;
  message?: string;
};

export default function App() {
  const [token, setToken] = useState<string | null>(getToken());
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleLogin(values: { username: string; password: string }) {
    setLoading(true);
    try {
      const payload = await login(values.username, values.password);
      setToken(payload.token);
      setUser(payload.user);
    } finally {
      setLoading(false);
    }
  }

  if (!token) {
    return (
      <main className="login-shell">
        <section className="login-panel">
          <div className="brand-row">
            <ShieldCheck size={30} />
            <Typography.Title level={2}>LexiFlow Agent</Typography.Title>
          </div>
          <Form layout="vertical" onFinish={handleLogin} initialValues={{ username: 'admin', password: 'admin123' }}>
            <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
              <Input autoComplete="username" />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true }]}>
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<LogIn size={16} />} loading={loading} block>
              登录
            </Button>
          </Form>
        </section>
      </main>
    );
  }

  return (
    <Layout className="app-shell">
      <Sider width={220} className="side-nav">
        <div className="side-brand">
          <ShieldCheck size={24} />
          <span>LexiFlow</span>
        </div>
        <nav>
          <a className="active"><Activity size={17} /> 工作台</a>
          <a><FileText size={17} /> 合同审查</a>
        </nav>
      </Sider>
      <Layout>
        <Header className="top-bar">
          <Typography.Text strong>{user?.displayName || '已登录用户'}</Typography.Text>
          <Button onClick={() => {
            clearToken();
            setToken(null);
            setUser(null);
          }}>退出</Button>
        </Header>
        <Content className="workspace">
          <section className="metric-grid">
            <Card><Statistic title="审查任务" value={0} /></Card>
            <Card><Statistic title="待审批" value={0} /></Card>
            <Card><Statistic title="高风险" value={0} /></Card>
          </section>
          <section className="main-grid">
            <Card title="Agent 执行时间线">
              <Timeline
                items={[
                  { children: '合同上传' },
                  { children: '条款抽取' },
                  { children: '规则检索' },
                  { children: '风险分析' }
                ]}
              />
            </Card>
            <Card title="当前权限">
              <List
                dataSource={user?.permissions || []}
                renderItem={(item) => <List.Item><Tag color="blue">{item}</Tag></List.Item>}
              />
            </Card>
          </section>
        </Content>
      </Layout>
    </Layout>
  );
}
