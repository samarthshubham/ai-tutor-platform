import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PageHeader } from './page-header';

@Component({
  imports: [PageHeader],
  template: `
    <tp-page-header title="Courses" subtitle="Grade 8">
      <button type="button">New course</button>
    </tp-page-header>
  `,
})
class HostWithActions {}

describe('PageHeader', () => {
  it('should show the title as the page heading', async () => {
    const fixture = TestBed.createComponent(PageHeader);
    fixture.componentRef.setInput('title', 'Home');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toBe('Home');
    expect(el.querySelector('p')).toBeNull();
  });

  it('should show the subtitle and projected actions', async () => {
    const fixture = TestBed.createComponent(HostWithActions);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toBe('Courses');
    expect(el.querySelector('p')?.textContent).toBe('Grade 8');
    expect(el.querySelector('.actions button')?.textContent).toBe('New course');
  });
});
