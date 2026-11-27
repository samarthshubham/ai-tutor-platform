import { ROLES, roleLabel } from './role';

describe('ROLES', () => {
  it('should list the five realm roles', () => {
    expect(ROLES).toEqual(['ADMIN', 'TEACHER', 'TUTOR', 'STUDENT', 'PARENT']);
  });

  it('should label a role in title case', () => {
    expect(ROLES.map(roleLabel)).toEqual([
      'Admin',
      'Teacher',
      'Tutor',
      'Student',
      'Parent',
    ]);
  });
});
