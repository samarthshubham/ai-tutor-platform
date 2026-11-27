import { Route } from '@angular/router';
import { Role } from '@tutor-web/api';

export interface NavigationItem {
  label: string;
  path: string;
}

export const HOME_ITEM: NavigationItem = { label: 'Home', path: '/home' };

export const ROLE_NAVIGATION: Record<Role, NavigationItem[]> = {
  ADMIN: [
    { label: 'Users', path: '/admin/users' },
    { label: 'Lesson review', path: '/admin/review' },
    { label: 'Courses', path: '/courses' },
    { label: 'AI usage', path: '/admin/ai-usage' },
  ],
  TEACHER: [
    { label: 'Courses', path: '/courses' },
    { label: 'Question bank', path: '/questions' },
    { label: 'Class progress', path: '/class-progress' },
  ],
  TUTOR: [
    { label: 'My students', path: '/students' },
    { label: 'Question bank', path: '/questions' },
  ],
  STUDENT: [
    { label: 'My courses', path: '/courses' },
    { label: 'Practice', path: '/practice' },
    { label: 'Progress', path: '/progress' },
    { label: 'Tutor chat', path: '/chat' },
  ],
  PARENT: [{ label: 'My children', path: '/students' }],
};

export function navigationFor(role: Role, routes: Route[]): NavigationItem[] {
  const existing = ROLE_NAVIGATION[role].filter((item) =>
    routes.some((route) => `/${route.path}` === item.path),
  );
  return existing.length > 0 ? existing : [HOME_ITEM];
}
