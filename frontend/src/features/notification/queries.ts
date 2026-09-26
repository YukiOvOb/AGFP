import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
export const notificationKeys = { all: ['notifications'] as const };
export function useUnread() {
  return useQuery({
    queryKey: [...notificationKeys.all, 'unread'],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/notifications/unread-count');
      return data!;
    },
    refetchInterval: 30000,
  });
}
