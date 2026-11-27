import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '@tutor-web/auth';
import { map } from 'rxjs';
import { navigationFor } from './navigation';

export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.whenReady().pipe(
    map((authenticated) => {
      if (!authenticated) {
        auth.login('/');
        return false;
      }
      const role = auth.role();
      if (role === null) {
        return false;
      }
      return router.parseUrl(
        auth.takeReturnUrl() ?? navigationFor(role, router.config)[0].path,
      );
    }),
  );
};
