import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { chainable, stubCypress } from '../../testing/cypress-stub';
import { clearTokenCache, tokenFor } from './token';

const TOKEN_URL =
  'http://localhost:8180/realms/tutor/protocol/openid-connect/token';

function tokenResponse(accessToken: string, expiresIn = 900) {
  return chainable({
    status: 200,
    body: { access_token: accessToken, expires_in: expiresIn },
  });
}

describe('tokenFor', () => {
  let cypress: ReturnType<typeof stubCypress>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-14T10:00:00Z'));
    clearTokenCache();
    cypress = stubCypress();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('uses the tutor-cli password grant with the demo password', () => {
    cypress.request.mockReturnValueOnce(tokenResponse('teacher-token'));

    const token = tokenFor('teacher1') as unknown as { value: string };

    expect(token.value).toBe('teacher-token');
    expect(cypress.request).toHaveBeenCalledWith({
      method: 'POST',
      url: TOKEN_URL,
      form: true,
      body: {
        grant_type: 'password',
        client_id: 'tutor-cli',
        username: 'teacher1',
        password: 'Teacher#2026',
      },
      log: false,
    });
  });

  it('asks the Keycloak configured in the Cypress env', () => {
    cypress = stubCypress({ keycloakUrl: 'http://kc.test' });
    cypress.request.mockReturnValueOnce(tokenResponse('token'));

    tokenFor('admin1');

    expect(cypress.request.mock.calls[0][0].url).toBe(
      'http://kc.test/realms/tutor/protocol/openid-connect/token',
    );
  });

  it('reuses a cached token until shortly before it expires', () => {
    cypress.request
      .mockReturnValueOnce(tokenResponse('first', 60))
      .mockReturnValueOnce(tokenResponse('second', 60));

    tokenFor('student1');
    vi.advanceTimersByTime(29_000);
    const cached = tokenFor('student1') as unknown as { value: string };

    expect(cached.value).toBe('first');
    expect(cypress.wrap).toHaveBeenCalledWith('first', { log: false });
    expect(cypress.request).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(2_000);
    const renewed = tokenFor('student1') as unknown as { value: string };

    expect(renewed.value).toBe('second');
    expect(cypress.request).toHaveBeenCalledTimes(2);
  });

  it('keeps one token per user', () => {
    cypress.request
      .mockReturnValueOnce(tokenResponse('admin-token'))
      .mockReturnValueOnce(tokenResponse('parent-token'));

    tokenFor('admin1');
    const parent = tokenFor('parent1') as unknown as { value: string };

    expect(parent.value).toBe('parent-token');
    expect(cypress.request).toHaveBeenCalledTimes(2);
  });

  it('refuses a user who is not a demo user without calling Keycloak', () => {
    expect(() => tokenFor('mallory')).toThrow('is not a demo user');
    expect(cypress.request).not.toHaveBeenCalled();
  });
});
