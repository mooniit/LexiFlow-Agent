import { Button, Form, Input, Typography, message } from 'antd';
import { LogIn, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getToken } from '../../api/client';
import { login } from '../../api/auth';

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    if (getToken()) {
      navigate('/dashboard', { replace: true });
    }
  }, [navigate]);

  async function handleLogin(values: { username: string; password: string }) {
    setLoading(true);
    try {
      await login(values.username, values.password);
      message.success('登录成功');
      navigate('/dashboard', { replace: true });
    } catch (err: any) {
      message.error(err.message || '登录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-panel">
        <div className="brand-row">
          <ShieldCheck size={30} />
          <Typography.Title level={2} style={{ margin: 0 }}>LexiFlow Agent</Typography.Title>
        </div>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 24 }}>
          企业合同与合规审查平台
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={handleLogin} initialValues={{ username: 'admin', password: 'admin123' }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input autoComplete="username" size="large" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="current-password" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" icon={<LogIn size={16} />} loading={loading} block size="large">
            登录
          </Button>
        </Form>
      </section>
    </main>
  );
}
