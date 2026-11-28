import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardTitle,
} from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { LessonApi } from '@tutor-web/api';
import { EmptyState, ErrorBanner, Loading, PageHeader } from '@tutor-web/ui';
import { LessonActions } from '../lessons/lesson-actions';
import { Pagination } from '../shared/pagination';
import { resourceError } from '../shared/resource-error';

@Component({
  selector: 'tp-admin-review',
  imports: [
    EmptyState,
    ErrorBanner,
    Loading,
    PageHeader,
    LessonActions,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    RouterLink,
    Pagination,
  ],
  template: `
    <tp-page-header title="Lesson review" />
    @if (queue.isLoading()) {
      <tp-loading label="Loading review queue…" />
    } @else if (error(queue); as failure) {
      <tp-error-banner [error]="failure" (retry)="queue.reload()" />
    } @else if (queue.hasValue()) {
      @if (queue.value().content.length === 0) {
        <tp-empty-state
          title="No lessons to review"
          message="Submitted lessons will appear here."
        />
      } @else {
        <div class="queue">
          @for (lesson of queue.value().content; track lesson.id) {
            <mat-card appearance="outlined">
              <mat-card-header
                ><mat-card-title
                  ><a [routerLink]="['/lessons', lesson.id]">{{
                    lesson.title
                  }}</a></mat-card-title
                ></mat-card-header
              >
              <mat-card-content
                ><tp-lesson-actions
                  [lesson]="lesson"
                  [reviewOnly]="true"
                  (changed)="refresh()"
              /></mat-card-content>
            </mat-card>
          }
        </div>
      }
      <tp-pagination [page]="queue.value()" (navigate)="page.set($event)" />
    }
  `,
  styles: `
    .queue {
      display: grid;
      gap: 16px;
    }
    mat-card-content {
      padding-block: 12px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminReview {
  private readonly api = inject(LessonApi);
  readonly page = signal(0);
  readonly queue = rxResource({
    params: this.page,
    stream: ({ params }) => this.api.reviewQueue(params),
  });
  readonly error = resourceError;

  refresh(): void {
    if (this.page() > 0) this.page.set(0);
    else this.queue.reload();
  }
}
