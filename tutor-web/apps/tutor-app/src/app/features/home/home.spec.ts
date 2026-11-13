import { TestBed } from '@angular/core/testing';
import { ApiError, Health, HealthApi } from '@tutor-web/api';
import { Observable, of, Subject, throwError } from 'rxjs';
import { Home } from './home';

const NETWORK_FAILURE: ApiError = {
  status: 0,
  code: 'NETWORK',
  message: 'Cannot reach the server. Check your connection and try again.',
};

describe('Home', () => {
  async function render(...responses: Observable<Health>[]) {
    const get = vi.fn();
    responses.forEach((response) => get.mockReturnValueOnce(response));
    TestBed.configureTestingModule({
      providers: [{ provide: HealthApi, useValue: { get } }],
    });
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    return { fixture, get, el: fixture.nativeElement as HTMLElement };
  }

  it('should show a loading state while the health check runs', async () => {
    const { el } = await render(new Subject<Health>());

    expect(el.querySelector('tp-loading')?.textContent).toContain(
      'Checking the API…',
    );
    expect(el.querySelector('[data-testid="api-status"]')).toBeNull();
  });

  it('should show "API: UP" when the API is healthy', async () => {
    const { el } = await render(of({ status: 'UP' }));

    expect(el.querySelector('h1')?.textContent).toBe('Home');
    expect(el.querySelector('[data-testid="api-status"]')?.textContent).toBe(
      'API: UP',
    );
    expect(el.querySelector('tp-loading')).toBeNull();
  });

  it('should show the error banner when the API cannot be reached', async () => {
    const { el } = await render(throwError(() => NETWORK_FAILURE));

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      NETWORK_FAILURE.message,
    );
    expect(el.querySelector('[data-testid="api-status"]')).toBeNull();
  });

  it('should check again on retry and show the status once the API is back', async () => {
    const { fixture, get, el } = await render(
      throwError(() => NETWORK_FAILURE),
      of({ status: 'UP' }),
    );

    el.querySelector<HTMLButtonElement>('[role="alert"] button')?.click();
    await fixture.whenStable();

    expect(get).toHaveBeenCalledTimes(2);
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelector('[data-testid="api-status"]')?.textContent).toBe(
      'API: UP',
    );
  });
});
