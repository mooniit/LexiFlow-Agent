INSERT INTO permission (code, name) VALUES
('contract:read', '查看合同'),
('contract:write', '管理合同'),
('review:read', '查看审查任务'),
('review:write', '管理审查任务'),
('approval:read', '查看审批'),
('approval:write', '处理审批'),
('knowledge:read', '查看知识库'),
('knowledge:write', '管理知识库'),
('trace:read', '查看 Agent Trace'),
('tool:execute', '执行审查工具'),
('admin:manage', '系统管理')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'contract:read',
    'contract:write',
    'review:read',
    'review:write',
    'approval:read',
    'approval:write',
    'knowledge:read',
    'knowledge:write',
    'trace:read',
    'tool:execute',
    'admin:manage'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'contract:read',
    'review:read',
    'approval:read',
    'approval:write',
    'knowledge:read',
    'trace:read',
    'tool:execute'
)
WHERE r.code = 'LEGAL_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'contract:read',
    'review:read',
    'knowledge:read',
    'trace:read',
    'tool:execute'
)
WHERE r.code = 'LEGAL_REVIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'contract:read',
    'contract:write',
    'review:read',
    'review:write',
    'knowledge:read'
)
WHERE r.code = 'BUSINESS_USER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'contract:read',
    'review:read',
    'knowledge:read',
    'trace:read'
)
WHERE r.code = 'VIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO app_user (username, password_hash, display_name, enabled)
VALUES (
    'admin',
    '{noop}admin123',
    '系统管理员',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
JOIN role r ON r.code = 'ADMIN'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_user_role_role_id ON user_role(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permission_permission_id ON role_permission(permission_id);
