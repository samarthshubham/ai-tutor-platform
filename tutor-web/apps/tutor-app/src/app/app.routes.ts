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
  { path: '**', redirectTo: '' },
];
