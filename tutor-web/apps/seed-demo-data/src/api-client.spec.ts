import { afterEach, describe, expect, it, vi } from 'vitest';
import { SeedApi } from './api-client';

const tokenResponse = () =>
  Response.json({ access_token: 'demo-token', expires_in: 60 });

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('SeedApi', () => {
  it('checks both services before preparing records', async () => {
    const fetch = vi
      .fn()
      .mockImplementation(() => Promise.resolve(Response.json({})));
    vi.stubGlobal('fetch', fetch);

    await new SeedApi().checkServices();

    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      'http://localhost:9190/actuator/health',
      'http://localhost:8180/realms/tutor/.well-known/openid-configuration',
    ]);
  });

  it('reports an unavailable API or Keycloak without printing credentials', async () => {
    const fetch = vi
      .fn()
      .mockRejectedValue(new TypeError('Network unavailable'));
    vi.stubGlobal('fetch', fetch);

    await expect(new SeedApi().checkServices()).rejects.toThrow(
      'API is unavailable at http://localhost:9190',
    );
    fetch.mockResolvedValueOnce(Response.json({ status: 'UP' }));
    await expect(new SeedApi().checkServices()).rejects.toThrow(
      'Keycloak is unavailable at http://localhost:8180',
    );
  });

  it('posts JSON with the current token, refreshes expired credentials, and handles bodyless transitions', async () => {
    vi.useFakeTimers();
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(Response.json({ id: 'course' }))
      .mockResolvedValueOnce(Response.json({ id: 'lesson' }))
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(Response.json({ id: 'user' }));
    vi.stubGlobal('fetch', fetch);
    const api = new SeedApi();

    await expect(
      api.post('teacher1', '/courses', { title: 'Demo' }),
    ).resolves.toEqual({ id: 'course' });
    await api.post('teacher1', '/lessons/lesson/submit');
    vi.advanceTimersByTime(31_000);
    await api.get('teacher1', '/me');

    expect(fetch.mock.calls[0][1].body).toEqual(
      new URLSearchParams({
        grant_type: 'password',
        client_id: 'tutor-cli',
        username: 'teacher1',
        password: 'Teacher#2026',
      }),
    );
    expect(fetch.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      headers: { Authorization: 'Bearer demo-token' },
      body: '{"title":"Demo"}',
    });
    expect(fetch.mock.calls[2][1].body).toBeUndefined();
    expect(fetch.mock.calls[3][0]).toContain('/protocol/openid-connect/token');
  });

  it('finds a record on a later page and returns undefined after searching all pages', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(
        Response.json({ content: [{ id: 1 }], totalPages: 2 }),
      )
      .mockResolvedValueOnce(
        Response.json({ content: [{ id: 2 }], totalPages: 2 }),
      )
      .mockResolvedValueOnce(Response.json({ content: [], totalPages: 0 }));
    vi.stubGlobal('fetch', fetch);
    const api = new SeedApi();

    await expect(
      api.find<{ id: number }>(
        'admin1',
        '/courses?filter=demo',
        (item) => item.id === 2,
      ),
    ).resolves.toEqual({ id: 2 });
    await expect(
      api.find('admin1', '/courses', () => true),
    ).resolves.toBeUndefined();
    expect(fetch.mock.calls[2][0]).toContain('?filter=demo&page=1&size=100');
    expect(fetch.mock.calls[3][0]).toContain('/courses?page=0&size=100');
  });

  it('reports a failed public API request with its method and status', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(tokenResponse())
        .mockResolvedValueOnce(new Response(null, { status: 409 })),
    );
    await expect(
      new SeedApi().post('teacher1', '/courses', { title: 'Duplicate' }),
    ).rejects.toThrow('API POST /api/courses failed (HTTP 409)');
  });
});
