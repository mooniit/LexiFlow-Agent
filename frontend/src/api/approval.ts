import { api } from './client';

export type ApprovalRequest = {
  id: number;
  reviewId: number;
  approvalType: string;
  status: string;
  approverId: number;
  requestedBy: number;
  comment: string;
  riskSummary: string;
  resolvedAt: string;
  createdAt: string;
  updatedAt: string;
};

export type ApprovalHistory = {
  id: number;
  approvalId: number;
  action: string;
  comment: string;
  actorId: number;
  createdAt: string;
};

export function listApprovals(status?: string, reviewId?: number) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (reviewId) params.set('reviewId', String(reviewId));
  const qs = params.toString();
  return api<ApprovalRequest[]>(`/api/approvals${qs ? `?${qs}` : ''}`);
}

export function getApproval(id: number) {
  return api<ApprovalRequest>(`/api/approvals/${id}`);
}

export function getApprovalHistory(id: number) {
  return api<ApprovalHistory[]>(`/api/approvals/${id}/history`);
}

export function approveRequest(id: number, comment?: string) {
  return api<ApprovalRequest>(`/api/approvals/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment: comment || '' }),
  });
}

export function rejectRequest(id: number, comment?: string) {
  return api<ApprovalRequest>(`/api/approvals/${id}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment: comment || '' }),
  });
}

export function requestRevision(id: number, comment?: string) {
  return api<ApprovalRequest>(`/api/approvals/${id}/request-revision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment: comment || '' }),
  });
}
