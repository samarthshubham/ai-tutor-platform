import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LessonApi } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { ErrorBanner, Loading, PageHeader } from '@tutor-web/ui';
import { map } from 'rxjs';
import { resourceError } from '../shared/resource-error';
import { LessonActions } from './lesson-actions';
import { Markdown } from './markdown';

@Component({
  selector: 'tp-lesson-reader',
  imports: [
    ErrorBanner,
    Loading,
    PageHeader,
    MatButton,
    MatChip,
    RouterLink,
    LessonActions,
    Markdown,
  ],
  templateUrl: './lesson-reader.html',
  styleUrl: './lessons.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LessonReader {
  private readonly api = inject(LessonApi);
  private readonly id = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((params) => params.get('lessonId') ?? ''),
    ),
    { requireSync: true },
  );
  readonly lesson = rxResource({
    params: this.id,
    stream: ({ params }) => this.api.get(params),
  });
  readonly role = inject(AuthService).role;
  readonly error = resourceError;
}
