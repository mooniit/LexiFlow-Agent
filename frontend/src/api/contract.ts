import { api } from './client';

export type Contract = {
  id: number;
  contractName: string;
  contractType: string;
  contractAmount: number;
  customerName: string;
  status: string;
  fileType: string;
  uploadedBy: number;
  createdAt: string;
  updatedAt: string;
};

export type ContractClause = {
  id: number;
  contractId: number;
  clauseType: string;
  clauseTitle: string;
  clauseText: string;
  extractedJson: string;
  createdAt: string;
};

export function uploadContract(
  file: File,
  meta: { contractName?: string; contractType?: string; contractAmount?: string; customerName?: string }
) {
  const form = new FormData();
  form.append('file', file);
  if (meta.contractName) form.append('contractName', meta.contractName);
  if (meta.contractType) form.append('contractType', meta.contractType);
  if (meta.contractAmount) form.append('contractAmount', meta.contractAmount);
  if (meta.customerName) form.append('customerName', meta.customerName);
  return api<Contract>('/api/contracts', { method: 'POST', body: form });
}

export function listContracts(status?: string) {
  const qs = status ? `?status=${status}` : '';
  return api<Contract[]>(`/api/contracts${qs}`);
}

export function getContract(id: number) {
  return api<Contract>(`/api/contracts/${id}`);
}

export function getOriginalText(id: number) {
  return api<{ text: string }>(`/api/contracts/${id}/original`);
}

export function parseContract(id: number) {
  return api<Contract>(`/api/contracts/${id}/parse`, { method: 'POST' });
}

export function extractClauses(id: number) {
  return api<ContractClause[]>(`/api/contracts/${id}/clauses/extract`, { method: 'POST' });
}

export function getClauses(id: number) {
  return api<ContractClause[]>(`/api/contracts/${id}/clauses`);
}

export function archiveContract(id: number) {
  return api<Contract>(`/api/contracts/${id}/archive`, { method: 'POST' });
}

export function deleteContract(id: number) {
  return api<void>(`/api/contracts/${id}`, { method: 'DELETE' });
}
