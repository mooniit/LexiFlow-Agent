import { api } from './client';

export type ReviewToolConfig = {
  id: string;
  toolName: string;
  requiredPermission: string;
  riskLevel: string;
  approvalRequired: boolean;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type SaveToolPayload = {
  toolName: string;
  requiredPermission: string;
  riskLevel: string;
  approvalRequired: boolean;
  enabled: boolean;
};

export function listReviewTools() {
  return api<ReviewToolConfig[]>('/api/admin/review-tools');
}

export function createReviewTool(payload: SaveToolPayload) {
  return api<ReviewToolConfig>('/api/admin/review-tools', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updateReviewTool(id: string, payload: SaveToolPayload) {
  return api<ReviewToolConfig>(`/api/admin/review-tools/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deleteReviewTool(id: string) {
  return api<void>(`/api/admin/review-tools/${id}`, { method: 'DELETE' });
}
