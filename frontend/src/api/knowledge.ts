import { api } from './client';

export type KnowledgeBase = {
  id: string;
  name: string;
  visibility: string;
  departmentId: string;
  allowedRoles: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type KnowledgeDocument = {
  id: string;
  knowledgeBaseId: string;
  title: string;
  documentType: string;
  documentStatus: string;
  filePath: string;
  metadata: string;
  createdAt: string;
};

export type DocumentChunk = {
  id: string;
  documentId: string;
  chunkIndex: number;
  content: string;
  embedding: unknown;
  metadata: string;
};

export type RetrievedChunk = {
  chunkId: string;
  documentId: string;
  content: string;
  score: number;
};

export type QaAnswer = {
  id: string;
  question: string;
  answer: string;
  references: RetrievedChunk[];
  createdAt: string;
};

export type QaHistory = {
  id: string;
  question: string;
  answer: string;
  referencesJson: string;
  retrievedChunks: string;
  feedback?: string;
  createdAt: string;
};

export function createKnowledgeBase(name: string, visibility?: string) {
  return api<KnowledgeBase>('/api/knowledge-bases', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, visibility: visibility || 'PRIVATE' }),
  });
}

export function listKnowledgeBases() {
  return api<KnowledgeBase[]>('/api/knowledge-bases');
}

export function uploadDocument(knowledgeBaseId: string, file: File, title?: string, documentType?: string) {
  const form = new FormData();
  form.append('file', file);
  if (title) form.append('title', title);
  if (documentType) form.append('documentType', documentType);
  return api<KnowledgeDocument>(`/api/knowledge-bases/${knowledgeBaseId}/documents`, {
    method: 'POST',
    body: form,
  });
}

export function listDocuments(knowledgeBaseId?: string) {
  const qs = knowledgeBaseId ? `?knowledgeBaseId=${knowledgeBaseId}` : '';
  return api<KnowledgeDocument[]>(`/api/knowledge-bases/documents${qs}`);
}

export function listChunks(documentId: string) {
  return api<DocumentChunk[]>(`/api/knowledge-bases/documents/${documentId}/chunks`);
}

export function searchKnowledge(query: string, reviewId?: string, limit?: number) {
  return api<RetrievedChunk[]>('/api/knowledge-bases/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reviewId: reviewId || null, query, limit: limit || 5 }),
  });
}

export function askKnowledge(question: string, knowledgeBaseId?: string, limit?: number) {
  return api<QaAnswer>('/api/knowledge-qa/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, knowledgeBaseId: knowledgeBaseId || null, limit: limit || 5 }),
  });
}

export function listQaHistory() {
  return api<QaHistory[]>('/api/knowledge-qa/history');
}

export function submitQaFeedback(id: string, feedback: 'HELPFUL' | 'NOT_HELPFUL') {
  return api<QaHistory>(`/api/knowledge-qa/history/${id}/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ feedback }),
  });
}

export function batchImport(knowledgeBaseId: string) {
  return api<Record<string, unknown>>(`/api/knowledge-bases/${knowledgeBaseId}/batch-import`, { method: 'POST' });
}
