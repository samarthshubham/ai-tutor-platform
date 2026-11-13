import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HealthApi } from '@tutor-web/api';
import { of } from 'rxjs';
import { App } from './app';
import { appRoutes } from './app.routes';

describe('App shell', () => {
  async function render(url = '/') {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(appRoutes),
        { provide: HealthApi, useValue: { get: () => of({ status: 'UP' }) } },
      ],
    });
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('should show the top bar with the app name', async () => {
    const { el } = await render();

    expect(el.querySelector('mat-toolbar')?.textContent).toContain('AI Tutor');
  });

  it('should list the placeholder navigation and mark the current page', async () => {
    const { el } = await render();

    const links = Array.from(
      el.querySelectorAll<HTMLAnchorElement>(
        'mat-nav-list[aria-label="Main navigation"] a',
      ),
    );
    expect(links.map((link) => link.textContent?.trim())).toEqual(['Home']);
    expect(links[0].getAttribute('href')).toBe('/');
    expect(links[0].getAttribute('aria-current')).toBe('page');
  });

  it('should show the home page at the root route', async () => {
    const { el } = await render();

    expect(el.querySelector('main tp-home')).not.toBeNull();
    expect(el.querySelector('[data-testid="api-status"]')?.textContent).toBe(
      'API: UP',
    );
  });

  it('should send unknown routes to the home page', async () => {
    await render('/does-not-exist');

    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('should toggle the side navigation from the menu button', async () => {
    const { fixture, el } = await render();
    const toggle = el.querySelector<HTMLButtonElement>(
      'button[aria-label="Toggle navigation"]',
    );
    expect(toggle?.getAttribute('aria-expanded')).toBe('true');

    toggle?.click();
    await fixture.whenStable();

    expect(toggle?.getAttribute('aria-expanded')).toBe('false');
  });

  it('should offer a skip link to the main content', async () => {
    const { el } = await render();

    expect(el.querySelector('a.skip-link')?.getAttribute('href')).toBe(
      '#main-content',
    );
    expect(el.querySelector('main#main-content')).not.toBeNull();
  });
});
