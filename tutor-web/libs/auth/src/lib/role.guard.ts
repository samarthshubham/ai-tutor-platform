import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '@tutor-web/api';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export function roleGuard(...roles: Role[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.whenReady().pipe(
      map((authenticated) => {
        if (!authenticated) {
          auth.login(state.url);
          return false;
        }
        const role = auth.role();
        if (role === null) {
          return false;
        }
        return roles.length === 0 || roles.includes(role)
          ? true
          : router.createUrlTree(['/forbidden']);
      }),
    );
  };
}
