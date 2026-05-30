import { api } from './client';

export type ObservabilitySummary = {
  status: string;
  generatedAt: string;
  counters: Record<string, number>;
  reviewStatuses: Record<string, number>;
  runtime: {
    uptimeMs: number;
    usedMemoryBytes: number;
    maxMemoryBytes: number;
    liveThreads?: number | null;
    systemCpuUsage?: number | null;
    processCpuUsage?: number | null;
  };
  actuatorLinks: Record<string, string>;
};

export function getObservabilitySummary() {
  return api<ObservabilitySummary>('/api/observability/summary');
}
