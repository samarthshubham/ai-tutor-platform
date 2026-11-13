export interface ApiError {
  status: number;
  code: string;
  message: string;
  fields?: Record<string, string>;
}

export const NETWORK_ERROR: ApiError = {
  status: 0,
  code: 'NETWORK',
  message: 'Cannot reach the server. Check your connection and try again.',
};

const UNEXPECTED_ERROR_MESSAGE = 'Something went wrong. Please try again.';

export function toApiError(status: number, body: unknown): ApiError {
  if (status === 0) {
    return NETWORK_ERROR;
  }
  if (!isErrorBody(body)) {
    return {
      status,
      code: 'INTERNAL_ERROR',
      message: UNEXPECTED_ERROR_MESSAGE,
    };
  }
  const error: ApiError = { status, code: body.code, message: body.message };
  if (body.fields) {
    error.fields = body.fields;
  }
  return error;
}

function isErrorBody(
  body: unknown,
): body is Pick<ApiError, 'code' | 'message' | 'fields'> {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const candidate = body as Record<string, unknown>;
  return (
    typeof candidate['code'] === 'string' &&
    typeof candidate['message'] === 'string'
  );
}
