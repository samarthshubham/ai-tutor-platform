import { describe, expect, it } from 'vitest';
import { createRunPrefix, runPrefix, withRunPrefix } from './run-prefix';

describe('run prefix', () => {
  it('combines the time and a random suffix', () => {
    expect(createRunPrefix(36 ** 3, () => 0.5)).toBe('e2e-1000-i000');
  });

  it('pads a small random suffix to four characters', () => {
    expect(createRunPrefix(35, () => 0)).toBe('e2e-z-0000');
  });

  it('differs between runs started at the same moment', () => {
    const now = Date.now();

    expect(createRunPrefix(now, () => 0.1)).not.toBe(
      createRunPrefix(now, () => 0.2),
    );
  });

  it('is fixed for the whole spec and prefixes names', () => {
    expect(runPrefix).toMatch(/^e2e-[0-9a-z]+-[0-9a-z]{4}$/);
    expect(withRunPrefix('Grade 8 Science')).toBe(
      `${runPrefix} Grade 8 Science`,
    );
  });
});
