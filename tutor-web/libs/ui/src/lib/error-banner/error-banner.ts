import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { ApiError } from '@tutor-web/api';

@Component({
  selector: 'tp-error-banner',
  imports: [MatButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error-banner" role="alert">
      <p>{{ error().message }}</p>
      <button matButton type="button" (click)="retry.emit()">Retry</button>
    </div>
  `,
  styles: `
    .error-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 8px 8px 8px 16px;
      border-radius: var(--mat-sys-corner-medium);
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      font: var(--mat-sys-body-medium);
    }
    p {
      margin: 0;
    }
    button {
      --mat-button-text-label-text-color: var(--mat-sys-on-error-container);
    }
  `,
})
export class ErrorBanner {
  readonly error = input.required<ApiError>();
  readonly retry = output<void>();
}
