import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { components } from '@/api/schema';

// Java records always expose these fields; optional fields in OpenAPI are made explicit for rendering.
export type Loan = Required<components['schemas']['LoanView']>;
export type Checkout = Required<components['schemas']['CheckoutCandidate']>;

export async function checkoutEquipment(item: Checkout, identityConfirmed: boolean) {
  return api.POST('/api/v1/loans', {
    body: {
      reservationId: item.reservationId,
      borrowerId: item.borrowerId,
      expectedVersion: item.version,
      identityConfirmed,
    },
  });
}
export async function receiveReturn(item: Loan, condition: string, note: string) {
  return api.POST('/api/v1/loans/{loanId}/return', {
    params: { path: { loanId: item.loanId } },
    body: { expectedVersion: item.version, condition, note: note.trim() || undefined },
  });
}
export function useLoans(activeOnly: boolean, page: number, q = '') {
  return useQuery({
    queryKey: ['loans', activeOnly, page, q],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/loans', {
        params: { query: { activeOnly, page, size: 20, q: q || undefined } },
      });
      return data! as Loan[];
    },
  });
}
export function useCheckouts(enabled: boolean, page: number, q = '') {
  return useQuery({
    queryKey: ['checkouts', page, q],
    enabled,
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/loans/checkouts', {
        params: { query: { page, size: 20, q: q || undefined } },
      });
      return data! as Checkout[];
    },
  });
}
