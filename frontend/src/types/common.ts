// Mirrors ApiError.java. Every non-2xx JSON response from the backend has this shape.
export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  fieldErrors: Record<string, string> | null
}
