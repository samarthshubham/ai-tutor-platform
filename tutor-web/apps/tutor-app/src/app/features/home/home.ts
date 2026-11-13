import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiError, HealthApi } from '@tutor-web/api';
import { ErrorBanner, Loading, PageHeader } from '@tutor-web/ui';

@Component({
  selector: 'tp-home',
  imports: [ErrorBanner, Loading, PageHeader],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <tp-page-header title="Home" />
    @if (error(); as error) {
      <tp-error-banner [error]="error" (retry)="load()" />
    } @else if (apiStatus(); as apiStatus) {
      <p data-testid="api-status">API: {{ apiStatus }}</p>
    } @else {
      <tp-loading label="Checking the API…" />
    }
  `,
})
export class Home {
  private readonly healthApi = inject(HealthApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly apiStatus = signal<string | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.apiStatus.set(null);
    this.error.set(null);
    this.healthApi
      .get()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (health) => this.apiStatus.set(health.status),
        error: (error: ApiError) => this.error.set(error),
      });
  }
}
