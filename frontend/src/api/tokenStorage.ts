// A single, small module owning where the JWT lives -- localStorage, since this API is a
// stateless Bearer-token API with no cookie/session support at all (see SecurityConfig.java on
// the backend). Kept in one place so nothing else in the app touches localStorage directly, and
// so swapping the storage mechanism later only means editing this file.
const TOKEN_KEY = 'audiotracking.token'

// window.localStorage, not the bare global -- newer Node versions ship their own experimental
// global `localStorage` that shadows the browser one and throws unless run with
// --localstorage-file, which breaks this under both jsdom (tests) and any future SSR context.
// Going through window sidesteps that entirely.

export function getToken(): string | null {
  return window.localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  window.localStorage.removeItem(TOKEN_KEY)
}
