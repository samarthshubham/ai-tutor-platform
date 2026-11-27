import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { registerCommands } from './commands';
import { loginAs, signInOnKeycloakPage } from './login';

interface Field {
  type: ReturnType<typeof vi.fn>;
  click: ReturnType<typeof vi.fn>;
  should: ReturnType<typeof vi.fn>;
}

function stubBrowser(env: Record<string, unknown> = {}) {
  const fields = new Map<string, Field>();
  const get = vi.fn((selector: string) => {
    if (!fields.has(selector)) {
      fields.set(selector, {
        type: vi.fn(),
        click: vi.fn(),
        should: vi.fn(),
      });
    }
    return fields.get(selector);
  });
  const origin = vi.fn(
    (_url: string, _options: unknown, body: (args: unknown) => void) => {
      body((_options as { args: unknown }).args);
    },
  );
  const session = vi.fn((_id: string, setup: () => void) => setup());
  const visit = vi.fn();
  vi.stubGlobal('cy', { get, origin, session, visit });
  const add = vi.fn();
  vi.stubGlobal('Cypress', {
    env: (name: string) => env[name],
    Commands: { add },
  });
  return { fields, get, origin, session, visit, add };
}

describe('Keycloak sign-in', () => {
  let browser: ReturnType<typeof stubBrowser>;

  beforeEach(() => {
    browser = stubBrowser();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('fills the Keycloak form on its own origin with the demo password', () => {
    signInOnKeycloakPage('teacher1');

    expect(browser.origin).toHaveBeenCalledWith(
      'http://localhost:8180',
      { args: { username: 'teacher1', password: 'Teacher#2026' } },
      expect.any(Function),
    );
    expect(browser.fields.get('#username')?.type).toHaveBeenCalledWith(
      'teacher1',
    );
    expect(browser.fields.get('#password')?.type).toHaveBeenCalledWith(
      'Teacher#2026',
      { log: false },
    );
    expect(browser.fields.get('#kc-login')?.click).toHaveBeenCalled();
  });

  it('uses the configured Keycloak url', () => {
    browser = stubBrowser({ keycloakUrl: 'http://kc.test:8180' });

    signInOnKeycloakPage('admin1');

    expect(browser.origin.mock.calls[0][0]).toBe('http://kc.test:8180');
  });

  it('rejects a user that is not a demo user', () => {
    expect(() => signInOnKeycloakPage('mallory')).toThrow(
      '"mallory" is not a demo user',
    );
  });

  it('caches the session per user and waits for the app shell', () => {
    loginAs('admin1');

    expect(browser.session).toHaveBeenCalledWith(
      'admin1',
      expect.any(Function),
    );
    expect(browser.visit).toHaveBeenCalledWith('/');
    expect(browser.fields.get('#kc-login')?.click).toHaveBeenCalled();
    expect(
      browser.fields.get('[data-testid="user-menu"]')?.should,
    ).toHaveBeenCalledWith('be.visible');
  });

  it('registers cy.loginAs', () => {
    registerCommands();

    expect(browser.add).toHaveBeenCalledWith('loginAs', loginAs);
  });
});
