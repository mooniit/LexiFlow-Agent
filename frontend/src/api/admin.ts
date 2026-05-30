import { api } from './client';

export type AdminUser = {
  id: string;
  username: string;
  displayName: string;
  departmentId?: string | null;
  enabled: boolean;
  roles: string[];
  permissions: string[];
  createdAt: string;
  updatedAt: string;
};

export type AdminRole = {
  id: string;
  code: string;
  name: string;
  permissions: string[];
};

export type AdminPermission = {
  id: string;
  code: string;
  name: string;
};

export type SaveUserPayload = {
  username?: string;
  password?: string;
  displayName: string;
  departmentId?: string | null;
  enabled: boolean;
  roles: string[];
};

export function listAdminUsers() {
  return api<AdminUser[]>('/api/admin/users');
}

export function createAdminUser(payload: SaveUserPayload & { username: string; password: string }) {
  return api<AdminUser>('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updateAdminUser(id: string, payload: SaveUserPayload) {
  return api<AdminUser>(`/api/admin/users/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteAdminUser(id: string) {
  return api<void>(`/api/admin/users/${id}`, { method: 'DELETE' });
}

export function listAdminRoles() {
  return api<AdminRole[]>('/api/admin/roles');
}

export function listAdminPermissions() {
  return api<AdminPermission[]>('/api/admin/permissions');
}
