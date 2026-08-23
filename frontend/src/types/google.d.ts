// The tiny slice of the Google Identity Services API (loaded at runtime via a <script> tag --
// see features/auth/GoogleSignInButton.tsx) that this app actually calls. Typed narrowly instead
// of reaching for `any`.
export {}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string
            callback: (response: { credential: string }) => void
          }) => void
          renderButton: (
            parent: HTMLElement,
            options: { theme?: string; size?: string; width?: number },
          ) => void
        }
      }
    }
  }
}
