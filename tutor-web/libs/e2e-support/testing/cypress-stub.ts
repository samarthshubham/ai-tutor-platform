import { vi } from 'vitest';

export interface FakeChainable<T> {
  value: T;
  then<R>(callback: (value: T) => R): R | FakeChainable<R>;
}

export function chainable<T>(value: T): FakeChainable<T> {
  return {
    value,
    then<R>(callback: (value: T) => R) {
      const result = callback(value);
      return isChainable(result) ? result : chainable(result);
    },
  };
}

function isChainable(value: unknown): value is FakeChainable<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'then' in value &&
    'value' in value
  );
}

export function stubCypress(env: Record<string, unknown> = {}) {
  const request = vi.fn((options: { url: string }) => {
    void options;
    return chainable({ status: 200, body: {} as unknown });
  });
  const wrap = vi.fn(<T>(value: T) => chainable(value));
  vi.stubGlobal('cy', { request, wrap });
  vi.stubGlobal('Cypress', { env: (name: string) => env[name] });
  return { request, wrap };
}
