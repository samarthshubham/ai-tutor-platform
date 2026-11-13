import { TestBed } from '@angular/core/testing';
import { Loading } from './loading';

describe('Loading', () => {
  async function render(label?: string) {
    const fixture = TestBed.createComponent(Loading);
    if (label !== undefined) {
      fixture.componentRef.setInput('label', label);
    }
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('should announce a default loading status', async () => {
    const el = await render();

    const status = el.querySelector('[role="status"]');
    expect(status?.textContent?.trim()).toBe('Loading…');
    expect(el.querySelector('mat-progress-spinner')).not.toBeNull();
  });

  it('should show a custom label', async () => {
    const el = await render('Loading courses…');

    expect(el.querySelector('[role="status"]')?.textContent?.trim()).toBe(
      'Loading courses…',
    );
  });
});
