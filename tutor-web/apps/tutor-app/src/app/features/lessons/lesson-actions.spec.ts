import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { LessonApi, LessonStatus, Role } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { of, Subject, throwError } from 'rxjs';
import { LessonActions } from './lesson-actions';

function render(role: Role, status: LessonStatus) {
  const dialog = { open: vi.fn() };
  const api = {
    transition: vi.fn().mockReturnValue(of({ id: 'l', status: 'DRAFT' })),
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { role: signal(role) } },
      { provide: LessonApi, useValue: api },
      { provide: MatDialog, useValue: dialog },
    ],
  });
  const fixture = TestBed.createComponent(LessonActions);
  fixture.componentRef.setInput('lesson', { id: 'l', status });
  return {
    fixture,
    api,
    dialog,
    component: fixture.componentInstance,
    element: fixture.nativeElement as HTMLElement,
  };
}

describe('LessonActions', () => {
  it('gives admins review actions only for lessons awaiting review', async () => {
    const { fixture, element, component, api, dialog } = render(
      'ADMIN',
      'IN_REVIEW',
    );
    fixture.componentRef.setInput('reviewOnly', true);
    dialog.open
      .mockReturnValueOnce({ afterClosed: () => of(undefined) })
      .mockReturnValueOnce({ afterClosed: () => of(' Explain roots. ') });
    await fixture.whenStable();
    expect(element.textContent).toContain('Publish');
    expect(element.textContent).not.toContain('Archive lesson');
    component.requestChanges();
    expect(api.transition).not.toHaveBeenCalled();
    component.requestChanges();
    expect(api.transition).toHaveBeenCalledWith(
      'l',
      'request-changes',
      'Explain roots.',
    );
    fixture.componentRef.setInput('lesson', { id: 'l', status: 'ARCHIVED' });
    await fixture.whenStable();
    expect(element.querySelector('button')).toBeNull();
  });

  it('allows a teacher to archive but not review, and retries a failed transition', async () => {
    const { fixture, component, element, api } = render('TEACHER', 'IN_REVIEW');
    const pending = new Subject();
    api.transition
      .mockReturnValueOnce(
        throwError(() => ({ status: 409, message: 'Lesson changed' })),
      )
      .mockReturnValueOnce(pending);
    await fixture.whenStable();
    expect(element.textContent).toContain('Archive lesson');
    expect(element.textContent).not.toContain('Publish');
    component.transition('archive');
    await fixture.whenStable();
    expect(element.querySelector('tp-error-banner')?.textContent).toContain(
      'Lesson changed',
    );
    component.mutation.retry();
    component.transition('archive');
    expect(api.transition).toHaveBeenCalledTimes(2);
    pending.complete();
  });
});
