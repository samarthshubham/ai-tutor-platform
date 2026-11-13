import { TestBed } from '@angular/core/testing';
import { ErrorBanner } from './error-banner';

describe('ErrorBanner', () => {
  async function render() {
    const fixture = TestBed.createComponent(ErrorBanner);
    fixture.componentRef.setInput('error', {
      status: 503,
      code: 'AI_UNAVAILABLE',
      message: 'The tutor is not available right now',
    });
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('should show the error message as an alert', async () => {
    const { el } = await render();

    expect(el.querySelector('[role="alert"] p')?.textContent).toBe(
      'The tutor is not available right now',
    );
  });

  it('should emit retry when the retry button is clicked', async () => {
    const { fixture, el } = await render();
    const retry = vi.fn();
    fixture.componentInstance.retry.subscribe(retry);

    const button = el.querySelector('button');
    expect(button?.textContent?.trim()).toBe('Retry');
    button?.click();

    expect(retry).toHaveBeenCalledOnce();
  });
});
