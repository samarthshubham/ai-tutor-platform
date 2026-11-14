import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { chainable, stubCypress } from '../../testing/cypress-stub';
import { apiRequest, apiRequestAs } from './api-client';
import { clearTokenCache } from './token';

describe('API client', () => {
  let cypress: ReturnType<typeof stubCypress>;

  beforeEach(() => {
    clearTokenCache();
    cypress = stubCypress();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('sends the token as a bearer header and never fails on status', () => {
    apiRequest({
      method: 'POST',
      path: '/api/courses',
      body: { name: 'Science' },
      token: 'abc',
    });

    expect(cypress.request).toHaveBeenCalledWith({
      method: 'POST',
      url: 'http://localhost:9190/api/courses',
      body: { name: 'Science' },
      headers: { Authorization: 'Bearer abc' },
      failOnStatusCode: false,
    });
  });

  it('sends no Authorization header without a token', () => {
    apiRequest({ method: 'GET', path: '/actuator/health' });

    expect(cypress.request).toHaveBeenCalledWith({
      method: 'GET',
      url: 'http://localhost:9190/actuator/health',
      body: undefined,
      headers: {},
      failOnStatusCode: false,
    });
  });

  it('calls the API configured in the Cypress env', () => {
    cypress = stubCypress({ apiUrl: 'http://api.test' });

    apiRequest({ method: 'GET', path: '/api/me' });

    expect(cypress.request.mock.calls[0][0].url).toBe('http://api.test/api/me');
  });

  it('signs in as a demo user and sends that token', () => {
    cypress.request
      .mockReturnValueOnce(
        chainable({
          status: 200,
          body: { access_token: 'admin-token', expires_in: 900 },
        }),
      )
      .mockReturnValueOnce(chainable({ status: 200, body: { reply: 'pong' } }));

    const response = apiRequestAs('admin1', {
      method: 'GET',
      path: '/api/ai/ping',
    }) as unknown as { value: { body: unknown } };

    expect(response.value.body).toEqual({ reply: 'pong' });
    expect(cypress.request).toHaveBeenLastCalledWith({
      method: 'GET',
      url: 'http://localhost:9190/api/ai/ping',
      body: undefined,
      headers: { Authorization: 'Bearer admin-token' },
      failOnStatusCode: false,
    });
  });
});
