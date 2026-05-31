import { api } from './client';

export type PromptTemplate = {
  id: string;
  name: string;
  version: string;
  scene: string;
  description?: string;
  templateContent: string;
  variables: string;
  outputConstraints: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PromptTemplatePayload = {
  name: string;
  version: string;
  scene: string;
  description?: string;
  templateContent: string;
  variables?: string;
  outputConstraints?: string;
  enabled?: boolean;
};

export function listPrompts(scene?: string) {
  const qs = scene ? `?scene=${encodeURIComponent(scene)}` : '';
  return api<PromptTemplate[]>(`/api/admin/prompts${qs}`);
}

export function createPrompt(payload: PromptTemplatePayload) {
  return api<PromptTemplate>('/api/admin/prompts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function updatePrompt(id: string, payload: PromptTemplatePayload) {
  return api<PromptTemplate>(`/api/admin/prompts/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function deletePrompt(id: string) {
  return api<void>(`/api/admin/prompts/${id}`, { method: 'DELETE' });
}
