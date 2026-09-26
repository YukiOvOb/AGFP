import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup, configure } from '@testing-library/react';
import { server } from './server';
configure({ asyncUtilTimeout: 5000 });
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
});
afterAll(() => server.close());
