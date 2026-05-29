import { api, createSSE } from './client';

export type ContractReview = {
  id: number;
  contractId: number;
  status: string;
  overallRisk: string;
  reportJson: string;
  failReason: string;
  createdAt: string;
  updatedAt: string;
};

export type AgentStep = {
  id: number;
  reviewId: number;
  stepType: string;
  status: string;
  inputSummary: string;
  outputSummary: string;
  durationMs: number;
  createdAt: string;
};

export type ClauseRisk = {
  id: number;
  reviewId: number;
  contractId: number;
  riskLevel: string;
  riskType: string;
  clauseName: string;
  clauseText: string;
  reason: string;
  suggestion: string;
  evidenceRules: string;
  requiresApproval: boolean;
  createdAt: string;
};

export function createReview(contractId: number) {
  return api<ContractReview>('/api/reviews', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contractId }),
  });
}

export function listReviews(contractId?: number) {
  const qs = contractId ? `?contractId=${contractId}` : '';
  return api<ContractReview[]>(`/api/reviews${qs}`);
}

export function getReview(id: number) {
  return api<ContractReview>(`/api/reviews/${id}`);
}

export function getReviewSteps(id: number) {
  return api<AgentStep[]>(`/api/reviews/${id}/steps`);
}

export function getReviewRisks(id: number) {
  return api<ClauseRisk[]>(`/api/reviews/${id}/risks`);
}

export function cancelReview(id: number) {
  return api<ContractReview>(`/api/reviews/${id}/cancel`, { method: 'POST' });
}

export function rerunReview(id: number) {
  return api<ContractReview>(`/api/reviews/${id}/rerun`, { method: 'POST' });
}

export function subscribeReviewEvents(reviewId: number) {
  return createSSE(`/api/reviews/${reviewId}/events`);
}
