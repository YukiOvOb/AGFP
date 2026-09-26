import createClient from 'openapi-fetch';
import type { paths } from './schema';
export type Problem = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: { field: string; message: string }[];
};
export class ApiError extends Error {
  constructor(
    public problem: Problem,
    public status: number,
  ) {
    super(problem.detail ?? problem.title ?? 'The request could not be completed.');
  }
}
let csrfPending: Promise<void> | undefined;
export function cookieToken() {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith('XSRF-TOKEN='))
    ?.slice('XSRF-TOKEN='.length);
}
export async function refreshCsrf() {
  csrfPending ??= fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    .then(async (r) => {
      if (!r.ok) throw new ApiError({ detail: 'Could not initialise a secure session.' }, r.status);
    })
    .finally(() => {
      csrfPending = undefined;
    });
  await csrfPending;
}
export const api = createClient<paths>({
  baseUrl: window.location.origin,
  credentials: 'same-origin',
  fetch: (request) => globalThis.fetch(request),
});
api.use({
  async onRequest({ request }) {
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method)) {
      if (!cookieToken()) await refreshCsrf();
      const token = cookieToken();
      if (!token)
        throw new ApiError(
          { detail: 'Cookies are required to sign in. Please enable cookies and try again.' },
          403,
        );
      request.headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
    }
    return request;
  },
  async onResponse({ response, request }) {
    if (
      response.status === 401 &&
      !new URL(request.url, window.location.origin).pathname.endsWith('/login')
    )
      window.dispatchEvent(new Event('serms:unauthenticated'));
    if (!response.ok) {
      let p: Problem = {};
      try {
        p = await response.clone().json();
      } catch {
        /* Empty upstream errors use a safe fallback. */
      }
      throw new ApiError(p, response.status);
    }
    return response;
  },
});
export function message(error: unknown) {
  return error instanceof Error ? error.message : 'Please try again later.';
}
