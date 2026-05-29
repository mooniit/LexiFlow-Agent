const TOKEN_KEY = 'lexiflow_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    ...((options?.headers as Record<string, string>) || {}),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(path, { ...options, headers });
  const json = await res.json();

  if (!res.ok || !json.success) {
    throw new Error(json.message || `请求失败 (${res.status})`);
  }
  return json.data as T;
}

export function createSSE(path: string) {
  const token = getToken();
  const url = new URL(path, window.location.origin);
  if (token) url.searchParams.set('token', token);
  return new EventSource(url.toString());
}
