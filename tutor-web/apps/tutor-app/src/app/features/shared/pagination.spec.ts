import { TestBed } from '@angular/core/testing';
import { Pagination } from './pagination';

describe('Pagination', () => {
  it('hides a single page and emits both directions on a middle page', async () => {
    const fixture = TestBed.createComponent(Pagination);
    const element = fixture.nativeElement as HTMLElement;
    const navigated = vi.fn();
    fixture.componentInstance.navigate.subscribe(navigated);
    fixture.componentRef.setInput('page', { page: 0, totalPages: 1 });
    await fixture.whenStable();
    expect(element.querySelector('nav')).toBeNull();
    fixture.componentRef.setInput('page', { page: 1, totalPages: 3 });
    await fixture.whenStable();
    for (const button of Array.from(element.querySelectorAll('button')))
      button.click();
    expect(navigated.mock.calls).toEqual([[0], [2]]);
  });
});
