import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { components } from '@/api/schema';
export const reservationKeys = { all: ['reservations'] as const };
export type ReservationStatus = components['schemas']['ReservationView']['status'];
export function useMyReservations(status: ReservationStatus | undefined, page: number) {
  return useQuery({
    queryKey: [...reservationKeys.all, 'mine', status ?? 'ALL', page],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/reservations/mine', {
        params: { query: { status, page, size: 20 } },
      });
      return data!;
    },
  });
}
