import { Route } from '@angular/router';
import { ROLES } from '@tutor-web/api';
import { roleGuard } from '@tutor-web/auth';
import { homeRedirectGuard } from './home-redirect.guard';

export const appRoutes: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [homeRedirectGuard],
    children: [],
  },
  {
    path: 'home',
    title: 'Home · AI Tutor',
    canActivate: [roleGuard(...ROLES)],
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'forbidden',
    title: 'No access · AI Tutor',
    canActivate: [roleGuard(...ROLES)],
    loadComponent: () =>
      import('./features/forbidden/forbidden').then((m) => m.Forbidden),
  },
  {
    path: 'admin/users',
    title: 'Users · AI Tutor',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () =>
      import('./features/admin-users/admin-users').then((m) => m.AdminUsers),
  },
  {
    path: 'courses',
    title: 'Courses · AI Tutor',
    canActivate: [roleGuard('ADMIN', 'TEACHER', 'STUDENT')],
    loadComponent: () =>
      import('./features/courses/course-list').then((m) => m.CourseList),
  },
  {
    path: 'courses/:courseId/lessons/new',
    title: 'New lesson · AI Tutor',
    canActivate: [roleGuard('TEACHER')],
    loadChildren: () =>
      import('./features/lessons/lesson-editor.routes').then(
        (m) => m.lessonEditorRoutes,
      ),
  },
  {
    path: 'courses/:courseId',
    title: 'Course · AI Tutor',
    canActivate: [roleGuard('ADMIN', 'TEACHER', 'STUDENT')],
    loadComponent: () =>
      import('./features/courses/course-detail').then((m) => m.CourseDetail),
  },
  {
    path: 'lessons/:lessonId/edit',
    title: 'Edit lesson · AI Tutor',
    canActivate: [roleGuard('TEACHER')],
    loadChildren: () =>
      import('./features/lessons/lesson-editor.routes').then(
        (m) => m.lessonEditorRoutes,
      ),
  },
  {
    path: 'lessons/:lessonId',
    title: 'Lesson · AI Tutor',
    canActivate: [roleGuard(...ROLES)],
    loadComponent: () =>
      import('./features/lessons/lesson-reader').then((m) => m.LessonReader),
  },
  {
    path: 'admin/review',
    title: 'Lesson review · AI Tutor',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () =>
      import('./features/admin-review/admin-review').then((m) => m.AdminReview),
  },
  { path: '**', redirectTo: '' },
];
