import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from './client';

describe('apiClient', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sends cookie credentials and parses an ETag response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'profile-1' }), { status: 200, headers: { 'content-type': 'application/json', etag: '"v3"' } }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await apiClient.get<{ id: string }>('/users/profile-1');

    expect(result).toEqual({ data: { id: 'profile-1' }, etag: '"v3"' });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/users/profile-1', expect.objectContaining({ credentials: 'include', method: 'GET' }));
  });

  it('obtains and supplies the CSRF token for unsafe requests', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'content-type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'profile-1' }), { status: 201, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await apiClient.post('/users', { firstName: 'Amina' });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/csrf');
    expect(fetchMock.mock.calls[1][1].headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
  });

  it('exposes RFC 9457 error detail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 422, detail: 'Email is already in use.', code: 'DUPLICATE_EMAIL' }), { status: 422, headers: { 'content-type': 'application/problem+json' } })));
    await expect(apiClient.get('/users')).rejects.toMatchObject({ status: 422, code: 'DUPLICATE_EMAIL', message: 'Email is already in use.' });
  });

  it('refreshes and retries an expired auth/me request exactly once', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401, statusText: 'Unauthorized' }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'content-type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ admin: { id: 'admin-1' } }), { status: 200, headers: { 'content-type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'admin-1' }), { status: 200, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient.get<{ id: string }>('/auth/me')).resolves.toEqual({ data: { id: 'admin-1' }, etag: null });
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/v1/auth/me', '/api/v1/auth/csrf', '/api/v1/auth/refresh', '/api/v1/auth/me']);
  });
});
