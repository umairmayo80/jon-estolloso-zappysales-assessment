import type { ApiResult, ProblemDetailResponse } from '../types';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/$/, '');
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly traceId?: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(problem: ProblemDetailResponse) {
    super(problem.detail || problem.title || 'The request could not be completed.');
    this.name = 'ApiError';
    this.status = problem.status;
    this.code = problem.code;
    this.traceId = problem.traceId;
    this.fieldErrors = problem.fieldErrors;
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  skipRefresh?: boolean;
}

function readCookie(name: string): string | undefined {
  const prefix = `${encodeURIComponent(name)}=`;
  const token = document.cookie.split('; ').find((entry) => entry.startsWith(prefix));
  return token ? decodeURIComponent(token.slice(prefix.length)) : undefined;
}

let csrfRequest: Promise<string> | undefined;

async function ensureCsrfToken(): Promise<string> {
  const cookieToken = readCookie(CSRF_COOKIE);
  if (cookieToken) return cookieToken;

  csrfRequest ??= fetch(`${API_BASE_URL}/auth/csrf`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
    .then(async (response) => {
      if (!response.ok) throw await toApiError(response);
      const payload = (await response.json()) as { token?: string };
      return readCookie(CSRF_COOKIE) ?? payload.token ?? '';
    })
    .finally(() => {
      csrfRequest = undefined;
    });

  const token = await csrfRequest;
  if (!token) {
    throw new ApiError({
      status: 500,
      title: 'CSRF initialization failed',
      detail: 'A secure request token was not available. Refresh the page and try again.',
      code: 'CSRF_TOKEN_MISSING',
    });
  }
  return token;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetailResponse = {
    status: response.status,
    title: response.statusText || 'Request failed',
  };
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json') || contentType.includes('problem+json')) {
    try {
      problem = { ...problem, ...((await response.json()) as ProblemDetailResponse) };
    } catch {
      // A malformed error body should not hide the HTTP response status.
    }
  }
  return new ApiError(problem);
}

function canHaveBody(method: string): boolean {
  return !['GET', 'HEAD'].includes(method);
}

function isUnsafe(method: string): boolean {
  return ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  const { body, headers: initialHeaders, skipRefresh = false, ...init } = options;
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(initialHeaders);
  headers.set('Accept', 'application/json');

  if (body !== undefined) headers.set('Content-Type', 'application/json');
  if (isUnsafe(method)) headers.set(CSRF_HEADER, await ensureCsrfToken());

  let response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    method,
    headers,
    body: body === undefined || !canHaveBody(method) ? undefined : JSON.stringify(body),
    credentials: 'include',
  });

  const refreshEligible = !['/auth/login', '/auth/refresh', '/auth/logout', '/auth/csrf'].includes(path);
  if (response.status === 401 && !skipRefresh && refreshEligible) {
    try {
      await request<unknown>('/auth/refresh', { method: 'POST', skipRefresh: true });
      response = await fetch(`${API_BASE_URL}${path}`, {
        ...init,
        method,
        headers,
        body: body === undefined || !canHaveBody(method) ? undefined : JSON.stringify(body),
        credentials: 'include',
      });
    } catch {
      // Keep the original response so callers consistently receive a 401 error.
    }
  }

  if (!response.ok) throw await toApiError(response);

  const hasBody = response.status !== 204 && response.headers.get('content-length') !== '0';
  const contentType = response.headers.get('content-type') ?? '';
  const data = hasBody && contentType.includes('json') ? ((await response.json()) as T) : (undefined as T);
  return { data, etag: response.headers.get('etag') };
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>(path, { ...options, method: 'POST', body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'DELETE' }),
};

export function getProblemMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  return error instanceof ApiError ? error.message : fallback;
}
