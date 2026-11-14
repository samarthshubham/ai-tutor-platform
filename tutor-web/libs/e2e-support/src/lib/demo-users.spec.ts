import { describe, expect, it } from 'vitest';
import { DEMO_USERS, demoPassword } from './demo-users';

describe('demoPassword', () => {
  it.each([
    ['admin1', 'Admin#2026'],
    ['teacher2', 'Teacher#2026'],
    ['tutor1', 'Tutor#2026'],
    ['student3', 'Student#2026'],
    ['parent1', 'Parent#2026'],
  ])('gives %s the password %s', (username, password) => {
    expect(demoPassword(username)).toBe(password);
  });

  it('knows all nine demo users', () => {
    expect(Object.keys(DEMO_USERS)).toHaveLength(9);
  });

  it('rejects a user who is not in the demo realm', () => {
    expect(() => demoPassword('mallory')).toThrow(
      '"mallory" is not a demo user',
    );
  });

  it('does not treat inherited object keys as users', () => {
    expect(() => demoPassword('toString')).toThrow('is not a demo user');
  });
});
