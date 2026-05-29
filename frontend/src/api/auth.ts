import { api, setToken } from './client';

export type UserProfile = {
  id: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
};

export type LoginResponse = {
  token: string;
  user: UserProfile;
};

export async function login(username: string, password: string) {
  const data = await api<LoginResponse>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  setToken(data.token);
  return data;
}

export function fetchMe() {
  return api<UserProfile>('/api/auth/me');
}
