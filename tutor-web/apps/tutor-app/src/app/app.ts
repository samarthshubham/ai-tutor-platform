import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatListItem, MatNavList } from '@angular/material/list';
import {
  MatSidenav,
  MatSidenavContainer,
  MatSidenavContent,
} from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

export interface NavigationItem {
  label: string;
  path: string;
}

export const PLACEHOLDER_NAVIGATION: NavigationItem[] = [
  { label: 'Home', path: '/' },
];

@Component({
  selector: 'tp-root',
  imports: [
    MatIcon,
    MatIconButton,
    MatListItem,
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
  protected readonly navigation = PLACEHOLDER_NAVIGATION;
  protected readonly navigationOpen = signal(true);

  protected toggleNavigation(): void {
    this.navigationOpen.update((open) => !open);
  }
}
