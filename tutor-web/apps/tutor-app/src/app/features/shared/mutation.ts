import { DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiError } from '@tutor-web/api';
import { finalize, Observable } from 'rxjs';

export class Mutation {
  private readonly destroyRef = inject(DestroyRef);
  private retryRequest?: () => void;
  readonly busy = signal(false);
  readonly error = signal<ApiError | null>(null);

  run<T>(request: () => Observable<T>, done: (value: T) => void): void {
    if (this.busy()) return;
    this.retryRequest = () => this.run(request, done);
    this.error.set(null);
    this.busy.set(true);
    request()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: done,
        error: (error: ApiError) => this.error.set(error),
      });
  }

  retry(): void {
    this.retryRequest?.();
  }

  clear(): void {
    this.retryRequest = undefined;
    this.error.set(null);
  }
}
