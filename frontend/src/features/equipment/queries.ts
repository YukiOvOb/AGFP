import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
export const equipmentKeys = { all: ['equipment'] as const };
export type SearchParams = {
  q?: string;
  category?: string;
  location?: string;
  from?: string;
  to?: string;
  availableOnly?: boolean;
  page: number;
};
export function useEquipmentSearch(params: SearchParams) {
  return useQuery({
    queryKey: [...equipmentKeys.all, 'search', params],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/equipment', {
        params: { query: { ...params, size: 20 } },
      });
      return data!;
    },
  });
}
export function useEquipmentFilters() {
  return useQuery({
    queryKey: [...equipmentKeys.all, 'filters'],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/equipment/filters');
      return data!;
    },
    staleTime: 5 * 60_000,
  });
}
export function useAvailability(id: string, from: string, to: string) {
  return useQuery({
    queryKey: [...equipmentKeys.all, 'availability', id, from, to],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/equipment/{id}/availability', {
        params: { path: { id }, query: { from, to } },
      });
      return data!;
    },
  });
}
