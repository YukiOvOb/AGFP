import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
export const currentUser = {
  userId: '00000000-0000-0000-0000-000000000001',
  email: 'wang@example.com',
  displayName: 'Wang Yuanmeng',
  roles: ['BORROWER'],
};
export const server = setupServer(
  http.get('/api/v1/auth/csrf', () => {
    document.cookie = 'XSRF-TOKEN=test-csrf; Path=/';
    return new HttpResponse(null, { status: 204 });
  }),
  http.get('/api/v1/auth/me', () => HttpResponse.json(currentUser)),
  http.get('/api/v1/notifications/unread-count', () => HttpResponse.json({ count: 0 })),
  http.get('/api/v1/notifications', () =>
    HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  ),
);
