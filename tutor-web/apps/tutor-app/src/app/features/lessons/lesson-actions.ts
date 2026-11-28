import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  TemplateRef,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { LessonAction, LessonApi, StaffLesson } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { ErrorBanner } from '@tutor-web/ui';
import { Mutation } from '../shared/mutation';

@Component({
  selector: 'tp-lesson-actions',
  imports: [
    ErrorBanner,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormField,
    MatLabel,
    MatInput,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './lesson-actions.html',
  styleUrl: './lessons.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LessonActions {
  readonly lesson = input.required<StaffLesson>();
  readonly reviewOnly = input(false);
  readonly changed = output<StaffLesson>();
  private readonly api = inject(LessonApi);
  private readonly role = inject(AuthService).role;
  private readonly dialog = inject(MatDialog);
  private readonly commentDialog =
    viewChild.required<TemplateRef<unknown>>('commentDialog');
  private readonly untilDestroyed = takeUntilDestroyed<string | undefined>();
  readonly teacher = computed(() => this.role() === 'TEACHER');
  readonly canReview = computed(
    () => this.role() === 'ADMIN' && this.lesson().status === 'IN_REVIEW',
  );
  readonly canArchive = computed(
    () =>
      !this.reviewOnly() &&
      this.lesson().status !== 'ARCHIVED' &&
      (this.teacher() || this.role() === 'ADMIN'),
  );
  readonly comment = inject(FormBuilder).nonNullable.control('', [
    Validators.required,
    Validators.maxLength(2000),
  ]);
  readonly mutation = new Mutation();

  transition(action: LessonAction, comment?: string): void {
    const id = this.lesson().id;
    this.mutation.run(
      () => this.api.transition(id, action, comment),
      (result) => this.changed.emit(result),
    );
  }

  requestChanges(): void {
    this.comment.reset();
    this.dialog
      .open<unknown, unknown, string>(this.commentDialog(), {
        ariaLabel: 'Request changes',
        width: '32rem',
      })
      .afterClosed()
      .pipe(this.untilDestroyed)
      .subscribe((comment) => {
        if (comment?.trim()) this.transition('request-changes', comment.trim());
      });
  }
}
