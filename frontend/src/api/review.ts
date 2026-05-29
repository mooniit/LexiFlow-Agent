import { api, createSSE } from './client';

export type ContractReview = {
  id: string;
  contractId: string;
  status: string;
  overallRisk: string;
  reportJson: string;
  failReason: string;
  createdAt: string;
  updatedAt: string;
};

export type AgentStep = {
  id: string;
  reviewId: string;
  stepType: string;
  status: string;
  inputSummary: string;
  outputSummary: string;
  durationMs: number;
  createdAt: string;
};

export type ClauseRisk = {
  id: string;
  reviewId: string;
  contractId: string;
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

export function createReview(contractId: string) {
  return api<ContractReview>('/api/reviews', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contractId }),
  });
}

export function listReviews(contractId?: string) {
  const qs = contractId ? `?contractId=${contractId}` : '';
  return api<ContractReview[]>(`/api/reviews${qs}`);
}

export function getReview(id: string) {
  return api<ContractReview>(`/api/reviews/${id}`);
}

export function getReviewSteps(id: string) {
  return api<AgentStep[]>(`/api/reviews/${id}/steps`);
}

export function getReviewRisks(id: string) {
  return api<ClauseRisk[]>(`/api/reviews/${id}/risks`);
}

export function cancelReview(id: string) {
  return api<ContractReview>(`/api/reviews/${id}/cancel`, { method: 'POST' });
}

export function rerunReview(id: string) {
  return api<ContractReview>(`/api/reviews/${id}/rerun`, { method: 'POST' });
}

export function subscribeReviewEvents(reviewId: string) {
  return createSSE(`/api/reviews/${reviewId}/events`);
}
