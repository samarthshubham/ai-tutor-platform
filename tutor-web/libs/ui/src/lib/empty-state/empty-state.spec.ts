import { TestBed } from '@angular/core/testing';
import { EmptyState } from './empty-state';

describe('EmptyState', () => {
  async function render(actionLabel?: string) {
    const fixture = TestBed.createComponent(EmptyState);
    fixture.componentRef.setInput('title', 'No courses yet');
    fixture.componentRef.setInput('message', 'Create a course to get started.');
    if (actionLabel) {
      fixture.componentRef.setInput('actionLabel', actionLabel);
    }
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('should show the title and message', async () => {
    const { el } = await render();

    expect(el.querySelector('h2')?.textContent).toBe('No courses yet');
    expect(el.querySelector('p')?.textContent).toBe(
      'Create a course to get started.',
    );
    expect(el.querySelector('section')?.getAttribute('aria-label')).toBe(
      'No courses yet',
    );
  });

  it('should show no button without an action', async () => {
    const { el } = await render();

    expect(el.querySelector('button')).toBeNull();
  });

  it('should emit the action when its button is clicked', async () => {
    const { fixture, el } = await render('New course');
    const action = vi.fn();
    fixture.componentInstance.action.subscribe(action);

    const button = el.querySelector('button');
    expect(button?.textContent?.trim()).toBe('New course');
    button?.click();

    expect(action).toHaveBeenCalledOnce();
  });
});
