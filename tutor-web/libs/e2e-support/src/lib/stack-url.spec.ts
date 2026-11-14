import { afterEach, describe, expect, it, vi } from 'vitest';
import { stubCypress } from '../../testing/cypress-stub';
import { stackUrl } from './stack-url';

describe('stackUrl', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('defaults to the local dev ports', () => {
    stubCypress();

    expect(stackUrl('apiUrl')).toBe('http://localhost:9190');
    expect(stackUrl('keycloakUrl')).toBe('http://localhost:8180');
    expect(stackUrl('webUrl')).toBe('http://localhost:4300');
  });

  it('uses the Cypress env value when one is set', () => {
    stubCypress({ apiUrl: 'http://api.test:1234' });

    expect(stackUrl('apiUrl')).toBe('http://api.test:1234');
  });

  it('ignores empty and non-string env values', () => {
    stubCypress({ apiUrl: '', webUrl: 4300 });

    expect(stackUrl('apiUrl')).toBe('http://localhost:9190');
    expect(stackUrl('webUrl')).toBe('http://localhost:4300');
  });
});
