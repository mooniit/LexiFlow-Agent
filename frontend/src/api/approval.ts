import { api } from './client';

export type ApprovalRequest = {
  id: string;
  reviewId: string;
  approvalType: string;
  status: string;
  approverId: string;
  requestedBy: string;
  comment: string;
  riskSummary: string;
  resolvedAt: string;
  createdAt: string;
  updatedAt: string;
};

export type ApprovalHistory = {
  id: string;
  approvalId: string;
  action: string;
  comment: string;
  actorId: string;
  createdAt: string;
};

export function listApprovals(status?: string, reviewId?: string) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (reviewId) params.set('reviewId', String(reviewId));
  const qs = params.toString();
  return api<ApprovalRequest[]>(`/api/approvals${qs ? `?${qs}` : ''}`);
}

export function getApproval(id: string) {
  return api<ApprovalRequest>(`/api/approvals/${id}`);
}

export function getApprovalHistory(id: string) {
  return api<ApprovalHistory[]>(`/api/approvals/${id}/history`);
}

export function approveRequest(id: string, comment?: string) {
  return api<ApprovalRequest>(`/api/approvals/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment: comment || '' }),
  });
}

export function rejectRequest(id: string, comment?: string) {
  return api<ApprovalRequest>(`/api/approvals/${id}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment: comment || '' }),
  });
}

export function requestRevision(id: string, comment?: string) {
  return api<ApprovalRequest>(`/api/approvals/${id}/request-revision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment: comment || '' }),
  });
}
