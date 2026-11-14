export const DEMO_USERS = {
  admin1: 'ADMIN',
  teacher1: 'TEACHER',
  teacher2: 'TEACHER',
  tutor1: 'TUTOR',
  student1: 'STUDENT',
  student2: 'STUDENT',
  student3: 'STUDENT',
  parent1: 'PARENT',
  parent2: 'PARENT',
} as const;

export type DemoUser = keyof typeof DEMO_USERS;

function isDemoUser(username: string): username is DemoUser {
  return Object.hasOwn(DEMO_USERS, username);
}

export function demoPassword(username: string): string {
  if (!isDemoUser(username)) {
    throw new Error(`"${username}" is not a demo user`);
  }
  const role = DEMO_USERS[username];
  return `${role[0]}${role.slice(1).toLowerCase()}#2026`;
}
