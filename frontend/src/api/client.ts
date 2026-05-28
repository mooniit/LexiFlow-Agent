const TOKEN_KEY = 'lexiflow_token';

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  message?: string;
};

export type LoginResponse = {
  token: string;
  user: {
    id: number;
    username: string;
    displayName: string;
    roles: string[];
    permissions: string[];
  };
};

export async function login(username: string, password: string) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const payload = (await response.json()) as ApiResponse<LoginResponse>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || '登录失败');
  }
  localStorage.setItem(TOKEN_KEY, payload.data.token);
  return payload.data;
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function createReviewEventSource(reviewId: string) {
  return new EventSource(`/api/reviews/${reviewId}/events`);
}

