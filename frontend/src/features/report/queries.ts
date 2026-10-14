import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { components } from '@/api/schema';
export type Report = components['schemas']['ReportView'];
export const reportKeys = {
  all: ['reports'] as const,
  period: (from: string, to: string) => [...reportKeys.all, from, to] as const,
};
/** Keeps the previous report on screen while another period loads. */
export function useOperationsReport(from: string, to: string) {
  return useQuery({
    queryKey: reportKeys.period(from, to),
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/reports', { params: { query: { from, to } } });
      if (!data) throw new Error('The server returned an empty response.');
      return data;
    },
  });
}
