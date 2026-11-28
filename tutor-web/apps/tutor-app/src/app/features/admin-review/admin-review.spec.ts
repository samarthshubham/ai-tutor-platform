import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LessonApi } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { of, Subject } from 'rxjs';
import { AdminReview } from './admin-review';

describe('AdminReview', () => {
  it('loads, retries, lists and refreshes the review queue', async () => {
    const pending = new Subject();
    const api = {
      reviewQueue: vi
        .fn()
        .mockReturnValueOnce(pending)
        .mockReturnValueOnce(
          of({
            content: [{ id: 'l', title: 'Plants', status: 'IN_REVIEW' }],
            page: 0,
            totalPages: 2,
          }),
        )
        .mockReturnValue(of({ content: [], page: 0, totalPages: 0 })),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { role: signal('ADMIN') } },
        { provide: LessonApi, useValue: api },
      ],
    });
    const fixture = TestBed.createComponent(AdminReview);
    const component = fixture.componentInstance;
    const element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    expect(element.querySelector('tp-loading')).not.toBeNull();
    pending.error({
      status: 503,
      code: 'UNAVAILABLE',
      message: 'Queue unavailable',
    });
    await fixture.whenStable();
    expect(element.querySelector('tp-error-banner')?.textContent).toContain(
      'Queue unavailable',
    );
    element.querySelector<HTMLButtonElement>('tp-error-banner button')?.click();
    await fixture.whenStable();
    expect(
      element.querySelector('a[href="/lessons/l"]')?.textContent,
    ).toContain('Plants');
    component.page.set(1);
    await fixture.whenStable();
    expect(api.reviewQueue).toHaveBeenLastCalledWith(1);
    component.refresh();
    await fixture.whenStable();
    expect(element.textContent).toContain('No lessons to review');
    expect(api.reviewQueue).toHaveBeenLastCalledWith(0);
  });
});
