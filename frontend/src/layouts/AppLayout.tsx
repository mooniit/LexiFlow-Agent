import { Layout, Typography, Button } from 'antd';
import { LogOut, ShieldCheck } from 'lucide-react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { clearToken } from '../api/client';
import { fetchMe, type UserProfile } from '../api/auth';
import { useEffect, useState } from 'react';
import { Activity, FileText, FileUp, Search, CheckCircle, Settings, Users, Gauge, BookOpen } from 'lucide-react';

const { Header, Sider, Content } = Layout;

export default function AppLayout() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchMe()
      .then(setUser)
      .catch(() => {
        clearToken();
        navigate('/login');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleLogout() {
    clearToken();
    navigate('/login');
  }

  const navItems = [
    { to: '/dashboard', icon: <Activity size={17} />, label: '工作台' },
    { to: '/contracts', icon: <FileText size={17} />, label: '合同管理' },
    { to: '/contracts/upload', icon: <FileUp size={17} />, label: '上传合同' },
    { to: '/approvals', icon: <CheckCircle size={17} />, label: '审批中心' },
    { to: '/knowledge', icon: <Search size={17} />, label: '知识库问答' },
    { to: '/knowledge/manage', icon: <BookOpen size={17} />, label: '规则库管理' },
    { to: '/admin/users', icon: <Users size={17} />, label: '用户权限' },
    { to: '/admin/tools', icon: <Settings size={17} />, label: '工具配置' },
    { to: '/admin/observability', icon: <Gauge size={17} />, label: '系统观测' },
  ];

  return (
    <Layout className="app-shell">
      <Sider width={220} className="side-nav">
        <div className="side-brand">
          <ShieldCheck size={24} />
          <span>LexiFlow</span>
        </div>
        <nav>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to !== '/contracts'}
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              {item.icon}
              {item.label}
            </NavLink>
          ))}
        </nav>
      </Sider>
      <Layout>
        <Header className="top-bar">
          <Typography.Text strong>{user?.displayName || '加载中...'}</Typography.Text>
          <Button onClick={handleLogout} icon={<LogOut size={16} />}>
            退出
          </Button>
        </Header>
        <Content className="workspace">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
