import { ApiRequestError } from '@/api/client'

// The backend already crafts every error message to be safe to show a user (see
// GlobalExceptionHandler.java -- validation messages, "already exists", "not found", and a
// deliberately generic message for 500/502 that never leaks a stack trace or SQL error). So the
// frontend's job here is just to trust that message, and only add its own wording for failures
// that never reached the backend at all (a network error, an unparseable response).
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return error.message
  }
  if (error instanceof TypeError) {
    return 'Could not reach the server. Check your connection and try again.'
  }
  return 'Something went wrong. Please try again.'
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 404
}

export function isForbidden(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 403
}
