export const ROLES = [
  'ADMIN',
  'TEACHER',
  'TUTOR',
  'STUDENT',
  'PARENT',
] as const;

export type Role = (typeof ROLES)[number];
