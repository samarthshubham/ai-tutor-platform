import { TestBed } from '@angular/core/testing';
import { Markdown } from './markdown';

describe('Markdown', () => {
  it('renders Markdown and removes raw script and event-handler markup', async () => {
    const fixture = TestBed.createComponent(Markdown);
    fixture.componentRef.setInput(
      'source',
      '## Plants\n\n**Roots**\n\n<script type="text/plain">marker</script>\n\n<img alt="raw" onerror="">',
    );
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h2')?.textContent).toBe('Plants');
    expect(element.querySelector('strong')?.textContent).toBe('Roots');
    expect(element.querySelector('script, [onerror]')).toBeNull();
    expect(element.textContent).not.toContain('marker');
  });
});
