import { ROLES } from './role';

describe('ROLES', () => {
  it('should list the five realm roles', () => {
    expect(ROLES).toEqual(['ADMIN', 'TEACHER', 'TUTOR', 'STUDENT', 'PARENT']);
  });
});
