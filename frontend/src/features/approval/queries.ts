import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { components } from '@/api/schema';

export type ReservationView = components['schemas']['ApprovalReservationSnapshot'];
export type ApprovalDetail = components['schemas']['DetailResponse'];
export type DecisionRequest = components['schemas']['DecisionRequest'];
export const approvalKeys = {
  all: ['approvals'] as const,
  pending: (page: number) => [...approvalKeys.all, 'pending', page] as const,
  detail: (id: string) => [...approvalKeys.all, 'detail', id] as const,
};

function required<T>(data: T | undefined): T {
  if (data === undefined) throw new Error('The server returned an empty response.');
  return data;
}

export function usePendingApprovals(page: number) {
  return useQuery({
    queryKey: approvalKeys.pending(page),
    queryFn: async () =>
      required(
        (
          await api.GET('/api/v1/approvals/pending', {
            params: { query: { page, size: 20, sort: 'startAt,asc' } },
          })
        ).data,
      ),
  });
}

export function useApprovalDetail(id: string) {
  return useQuery({
    queryKey: approvalKeys.detail(id),
    queryFn: async () =>
      required(
        (
          await api.GET('/api/v1/approvals/{reservationId}', {
            params: { path: { reservationId: id } },
          })
        ).data,
      ),
  });
}

export function useDecision(id: string) {
  const client = useQueryClient();
  return useMutation({
    retry: false,
    mutationFn: async (body: DecisionRequest) => {
      const result = required(
        (
          await api.POST('/api/v1/approvals/{reservationId}/decision', {
            params: { path: { reservationId: id } },
            body,
          })
        ).data,
      );
      if (!result.reservation || !result.decision)
        throw new Error(
          'Unable to verify the saved decision. Refresh this request before continuing.',
        );
      return { reservation: result.reservation, decision: result.decision };
    },
    onSuccess: async (result) => {
      client.setQueryData<ApprovalDetail>(approvalKeys.detail(id), {
        ...result.reservation,
        decision: result.decision,
      });
      await client.invalidateQueries({ queryKey: approvalKeys.all });
    },
  });
}
