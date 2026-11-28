import { Resource } from '@angular/core';
import { ApiError, toApiError } from '@tutor-web/api';

export function resourceError(
  resource: Resource<unknown>,
): ApiError | undefined {
  const error = resource.error();
  if (!error) return undefined;

  // Angular wraps errors thrown by the API interceptor in Error.cause.
  const cause = error.cause;
  if (
    typeof cause === 'object' &&
    cause !== null &&
    'status' in cause &&
    typeof cause.status === 'number'
  ) {
    return toApiError(cause.status, cause);
  }
  return toApiError(500, null);
}
