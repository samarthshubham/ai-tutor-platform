export const ROLES = [
  'ADMIN',
  'TEACHER',
  'TUTOR',
  'STUDENT',
  'PARENT',
] as const;

export type Role = (typeof ROLES)[number];

export function roleLabel(role: Role): string {
  return `${role[0]}${role.slice(1).toLowerCase()}`;
}
