-- Demo users for role-based workflow verification.
-- Passwords use the existing {noop} encoder style for local development only.

INSERT INTO app_user (username, password_hash, display_name, department_id, enabled)
VALUES
('legal_manager', '{noop}manager123', '法务主管', 101, TRUE),
('legal_reviewer', '{noop}reviewer123', '法务审查员', 101, TRUE),
('business_user', '{noop}business123', '业务提交人', 201, TRUE),
('viewer', '{noop}viewer123', '只读观察员', 301, TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
JOIN role r ON r.code = 'LEGAL_MANAGER'
WHERE u.username = 'legal_manager'
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
JOIN role r ON r.code = 'LEGAL_REVIEWER'
WHERE u.username = 'legal_reviewer'
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
JOIN role r ON r.code = 'BUSINESS_USER'
WHERE u.username = 'business_user'
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
JOIN role r ON r.code = 'VIEWER'
WHERE u.username = 'viewer'
ON CONFLICT DO NOTHING;
