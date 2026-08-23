import type { ApiError } from '@/types/common'
import { clearToken, getToken } from './tokenStorage'

const BASE_URL = import.meta.env.VITE_API_BASE_URL

// Carries the backend's actual status/message/fieldErrors through to callers, instead of a
// generic fetch failure -- this is what lets a form show "username already taken" instead of
// just "something went wrong".
export class ApiRequestError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string> | null

  constructor(status: number, message: string, fieldErrors: Record<string, string> | null) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

// Set once by AuthProvider (see features/auth) so that ANY request across the whole app that
// comes back 401 triggers the same sign-out + redirect-to-login, without every API module or
// component needing to know how auth works. This is the app's one and only reaction to 401 --
// see the "For 401: redirect to login" requirement.
let unauthorizedHandler: (() => void) | null = null
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  isFormData?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let body: BodyInit | undefined
  if (options.body !== undefined) {
    if (options.isFormData) {
      // No Content-Type set here on purpose -- the browser fills in the multipart boundary
      // itself; setting it manually breaks the upload.
      body = options.body as FormData
    } else {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify(options.body)
    }
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body,
  })

  if (response.status === 401) {
    clearToken()
    unauthorizedHandler?.()
  }

  if (!response.ok) {
    let apiError: ApiError | null = null
    try {
      apiError = (await response.json()) as ApiError
    } catch {
      // Non-JSON error body (rare, e.g. a proxy/gateway error) -- fall through to a generic message.
    }
    throw new ApiRequestError(
      response.status,
      apiError?.message ?? `Request failed with status ${response.status}`,
      apiError?.fieldErrors ?? null,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

// The only way the rest of the app is allowed to talk to the backend -- no component or feature
// module calls fetch()/axios directly, so base URL, auth header, JSON handling, and error
// shaping all live in exactly one place.
export const api = {
  get: <T>(path: string): Promise<T> => request<T>(path),
  post: <T>(path: string, body?: unknown): Promise<T> => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown): Promise<T> => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string): Promise<T> => request<T>(path, { method: 'DELETE' }),
  postForm: <T>(path: string, formData: FormData): Promise<T> =>
    request<T>(path, { method: 'POST', body: formData, isFormData: true }),
}
