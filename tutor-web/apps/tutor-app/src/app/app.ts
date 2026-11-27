import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatListItem, MatNavList } from '@angular/material/list';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import {
  MatSidenav,
  MatSidenavContainer,
  MatSidenavContent,
} from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import {
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { roleLabel } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { ErrorBanner, Loading } from '@tutor-web/ui';
import { navigationFor } from './navigation';

@Component({
  selector: 'tp-root',
  imports: [
    ErrorBanner,
    Loading,
    MatButton,
    MatIcon,
    MatIconButton,
    MatListItem,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    MatNavList,
    MatSidenav,
    MatSidenavContainer,
    MatSidenavContent,
    MatToolbar,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly navigation = computed(() => {
    const role = this.auth.role();
    return role === null ? [] : navigationFor(role, this.router.config);
  });
  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role === null ? '' : roleLabel(role);
  });
  protected readonly navigationOpen = signal(true);

  protected toggleNavigation(): void {
    this.navigationOpen.update((open) => !open);
  }

  protected reload(): void {
    window.location.reload();
  }
}
