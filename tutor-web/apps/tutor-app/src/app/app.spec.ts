import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ApiError, HealthApi, Role, User } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { of } from 'rxjs';
import { App } from './app';
import { appRoutes } from './app.routes';

const NAMES: Record<Role, string> = {
  ADMIN: 'Ada Admin',
  TEACHER: 'Tara Teacher',
  TUTOR: 'Uma Tutor',
  STUDENT: 'Sam Student',
  PARENT: 'Pat Parent',
};

function userWithRole(role: Role): User {
  return {
    id: `id-${role}`,
    username: role.toLowerCase(),
    displayName: NAMES[role],
    email: null,
    role,
    schoolId: 's1',
  };
}

describe('App shell', () => {
  function authFor(role: Role | null) {
    const user = signal(role === null ? null : userWithRole(role));
    return {
      user,
      role: signal(role),
      checking: signal(false),
      error: signal<ApiError | null>(null),
      whenReady: () => of(role !== null),
      login: vi.fn(),
      logout: vi.fn(),
      takeReturnUrl: () => null,
    };
  }

  async function render(role: Role | null, url = '/home') {
    const auth = authFor(role);
    TestBed.configureTestingModule({
      providers: [
        provideRouter(appRoutes),
        { provide: AuthService, useValue: auth },
        { provide: HealthApi, useValue: { get: () => of({ status: 'UP' }) } },
      ],
    });
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
    return { fixture, auth, el: fixture.nativeElement as HTMLElement };
  }

  const links = (el: HTMLElement) =>
    Array.from(
      el.querySelectorAll<HTMLAnchorElement>(
        'mat-nav-list[aria-label="Main navigation"] a',
      ),
    );

  it('should show the top bar with the app name and the user', async () => {
    const { el } = await render('TEACHER');

    expect(el.querySelector('mat-toolbar')?.textContent).toContain('AI Tutor');
    expect(
      el.querySelector('[data-testid="user-menu"]')?.textContent,
    ).toContain('Tara Teacher');
  });

  it('should show the role and a sign out item in the user menu', async () => {
    const { fixture, auth, el } = await render('TEACHER');

    el.querySelector<HTMLButtonElement>('[data-testid="user-menu"]')?.click();
    await fixture.whenStable();

    const panel = document.querySelector('.mat-mdc-menu-panel');
    expect(panel?.querySelector('[data-testid="user-role"]')?.textContent).toBe(
      'Teacher',
    );
    panel?.querySelector<HTMLButtonElement>('button[mat-menu-item]')?.click();
    expect(auth.logout).toHaveBeenCalled();
  });

  it.each([
    ['ADMIN', ['Users']],
    ['TEACHER', ['Home']],
    ['TUTOR', ['Home']],
    ['STUDENT', ['Home']],
    ['PARENT', ['Home']],
  ] as [Role, string[]][])(
    'should show the navigation of %s',
    async (role, labels) => {
      const { el } = await render(role);

      expect(links(el).map((link) => link.textContent?.trim())).toEqual(labels);
    },
  );

  it('should mark the current page', async () => {
    const { el } = await render('TEACHER');

    expect(links(el)[0].getAttribute('aria-current')).toBe('page');
  });

  it('should show the home page and its API status', async () => {
    const { el } = await render('TEACHER');

    expect(el.querySelector('main tp-home')).not.toBeNull();
    expect(el.querySelector('[data-testid="api-status"]')?.textContent).toBe(
      'API: UP',
    );
  });

  it('should show the forbidden page for a teacher on /admin/users', async () => {
    const { el } = await render('TEACHER', '/admin/users');

    expect(TestBed.inject(Router).url).toBe('/forbidden');
    expect(el.querySelector('main tp-forbidden')).not.toBeNull();
  });

  it('should open the users screen for an admin', async () => {
    const { el } = await render('ADMIN', '/admin/users');

    expect(el.querySelector('main tp-admin-users')).not.toBeNull();
  });

  it('should send unknown routes to the role home', async () => {
    await render('TEACHER', '/does-not-exist');

    expect(TestBed.inject(Router).url).toBe('/home');
  });

  it('should show a loading state instead of the shell without a user', async () => {
    const { el } = await render(null, '/');

    expect(el.querySelector('tp-loading')?.textContent).toContain(
      'Checking your session…',
    );
    expect(el.querySelector('mat-toolbar')).toBeNull();
    expect(el.querySelector('mat-nav-list')).toBeNull();
  });

  it('should show the error banner when the user could not be loaded', async () => {
    const { fixture, auth, el } = await render(null, '/');
    auth.error.set({ status: 503, code: 'UNAVAILABLE', message: 'API down' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'API down',
    );
    expect(el.querySelector('tp-loading')).toBeNull();
  });

  it('should toggle the side navigation from the menu button', async () => {
    const { fixture, el } = await render('TEACHER');
    const toggle = el.querySelector<HTMLButtonElement>(
      'button[aria-label="Toggle navigation"]',
    );
    expect(toggle?.getAttribute('aria-expanded')).toBe('true');

    toggle?.click();
    await fixture.whenStable();

    expect(toggle?.getAttribute('aria-expanded')).toBe('false');
  });

  it('should offer a skip link to the main content', async () => {
    const { el } = await render('TEACHER');

    expect(el.querySelector('a.skip-link')?.getAttribute('href')).toBe(
      '#main-content',
    );
    expect(el.querySelector('main#main-content')).not.toBeNull();
  });
});
