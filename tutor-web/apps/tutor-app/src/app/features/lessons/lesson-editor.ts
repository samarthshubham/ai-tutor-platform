import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import {
  ActivatedRoute,
  CanDeactivateFn,
  Router,
  RouterLink,
} from '@angular/router';
import { CourseApi, Lesson, LessonApi } from '@tutor-web/api';
import { EmptyState, ErrorBanner, Loading, PageHeader } from '@tutor-web/ui';
import { map, Observable, of, switchMap } from 'rxjs';
import { Mutation } from '../shared/mutation';
import { resourceError } from '../shared/resource-error';
import { Markdown } from './markdown';

@Component({
  selector: 'tp-lesson-editor',
  imports: [
    EmptyState,
    ErrorBanner,
    Loading,
    PageHeader,
    MatButton,
    MatFormField,
    MatLabel,
    MatInput,
    MatSelect,
    MatOption,
    ReactiveFormsModule,
    RouterLink,
    Markdown,
  ],
  templateUrl: './lesson-editor.html',
  styleUrl: './lessons.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(window:beforeunload)': 'beforeUnload($event)' },
})
export class LessonEditor {
  private readonly api = inject(LessonApi);
  private readonly courses = inject(CourseApi);
  private readonly router = inject(Router);
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly params = toSignal(inject(ActivatedRoute).paramMap, {
    requireSync: true,
  });
  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    topicId: ['', Validators.required],
    bodyMarkdown: ['', [Validators.required, Validators.maxLength(50000)]],
  });
  readonly markdown = toSignal(this.form.controls.bodyMarkdown.valueChanges, {
    initialValue: '',
  });
  readonly editor = rxResource({
    params: this.params,
    stream: ({ params }) => {
      const id = params.get('lessonId');
      const lesson: Observable<Lesson | null> = id
        ? this.api.get(id)
        : of(null);
      return lesson.pipe(
        switchMap((lesson) =>
          this.courses
            .get(lesson?.courseId ?? params.get('courseId') ?? '')
            .pipe(map((course) => ({ course, lesson }))),
        ),
      );
    },
  });
  readonly saved = signal(false);
  readonly mutation = new Mutation();
  readonly error = resourceError;

  constructor() {
    effect(() => {
      if (!this.editor.hasValue()) return;
      const { lesson } = this.editor.value();
      this.form.reset({
        title: lesson?.title ?? '',
        topicId: lesson?.topicId ?? '',
        bodyMarkdown: lesson?.bodyMarkdown ?? '',
      });
      this.saved.set(false);
    });
  }

  save(): void {
    if (!this.editor.hasValue() || this.form.invalid) return;
    const { lesson, course } = this.editor.value();
    const fields = this.form.getRawValue();
    if (!fields.title.trim()) return;
    this.mutation.run(
      () =>
        lesson
          ? this.api.update(lesson.id, fields)
          : this.api.create(course.id, fields),
      (result) => {
        this.form.markAsPristine();
        this.saved.set(true);
        if (!lesson)
          void this.router.navigate(['/lessons', result.id, 'edit'], {
            replaceUrl: true,
          });
      },
    );
  }

  submit(id: string): void {
    if (this.form.dirty) return;
    this.mutation.run(
      () => this.api.transition(id, 'submit'),
      () => {
        void this.router.navigate(['/lessons', id]);
      },
    );
  }

  canLeave(): boolean {
    return (
      !this.form.dirty ||
      (this.window?.confirm('Leave this lesson and discard unsaved changes?') ??
        false)
    );
  }

  beforeUnload(event: BeforeUnloadEvent): void {
    if (this.form.dirty) event.preventDefault();
  }
}

export const canLeaveLesson: CanDeactivateFn<LessonEditor> = (component) =>
  component.canLeave();
