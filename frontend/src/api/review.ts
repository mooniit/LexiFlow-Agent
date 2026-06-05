import { api, createSSE } from './client';

export type ContractReview = {
  id: string;
  contractId: string;
  status: string;
  overallRiskLevel: string;
  resultSummary: string;
  failureReason: string;
  progressPercent: number;
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
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
  updatedAt?: string;
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

export type ApprovalSummary = {
  id: string;
  reviewId: string;
  approvalType: string;
  status: string;
  comment?: string;
  createdAt: string;
};

export type ReviewReport = {
  review: ContractReview;
  contract: {
    id: string;
    contractName: string;
    contractType?: string;
    contractAmount?: number;
    customerName?: string;
    status: string;
  };
  risks: ClauseRisk[];
  approvals: ApprovalSummary[];
  summary: string;
  finalConclusion: string;
};

export type LlmCallLog = {
  id: string;
  provider?: string;
  modelName?: string;
  promptVersion?: string;
  requestBody: string;
  responseBody: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  latencyMs?: number | string;
  success: boolean;
  errorMessage?: string;
  createdAt: string;
};

export type ToolCallLog = {
  id: string;
  toolName: string;
  arguments: string;
  result: string;
  permissionResult: string;
  latencyMs?: number | string;
  success: boolean;
  errorMessage?: string;
  createdAt: string;
};

export type RetrievalLog = {
  id: string;
  queryText: string;
  filterConditions: string;
  retrievedChunks: string;
  latencyMs?: number | string;
  createdAt: string;
};

export type ReviewEvent = {
  reviewId: string;
  type: string;
  message: string;
  occurredAt: string;
};

export type ReviewTrace = {
  review: ContractReview;
  steps: AgentStep[];
  transitions: Array<{ id: string; fromStatus?: string; toStatus: string; reason?: string; createdAt: string }>;
  llmCalls: LlmCallLog[];
  toolCalls: ToolCallLog[];
  retrievalLogs: RetrievalLog[];
  metrics: {
    stepCount: number;
    llmCallCount: number;
    toolCallCount: number;
    retrievalCount: number;
    totalTokens: number;
    totalLatencyMs: number | string;
    stepDurationMs: number | string;
    wallClockMs: number | string;
    toolLatencyMs: number | string;
    llmLatencyMs: number | string;
    retrievalLatencyMs: number | string;
  };
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

export function getReviewReport(id: string) {
  return api<ReviewReport>(`/api/reviews/${id}/report`);
}

export function getReviewTrace(id: string) {
  return api<ReviewTrace>(`/api/reviews/${id}/trace`);
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
