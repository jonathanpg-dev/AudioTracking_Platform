import { useEffect, useRef } from 'react'

const GOOGLE_SCRIPT_SRC = 'https://accounts.google.com/gsi/client'

interface GoogleSignInButtonProps {
  onCredential: (idToken: string) => void
}

// Wraps Google Identity Services' own rendered button (loaded at runtime, not bundled) --
// mirrors the flow already proven out in the backend's manual-testing/google-login-test.html,
// just wired into React instead of a raw callback global.
export function GoogleSignInButton({ onCredential }: GoogleSignInButtonProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID

  useEffect(() => {
    if (!clientId) {
      return
    }

    let cancelled = false

    function render() {
      if (cancelled || !window.google || !containerRef.current) {
        return
      }
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (response) => onCredential(response.credential),
      })
      window.google.accounts.id.renderButton(containerRef.current, {
        theme: 'outline',
        size: 'large',
        width: 320,
      })
    }

    if (window.google) {
      render()
      return
    }

    const existing = document.querySelector<HTMLScriptElement>(`script[src="${GOOGLE_SCRIPT_SRC}"]`)
    const script = existing ?? document.createElement('script')
    if (!existing) {
      script.src = GOOGLE_SCRIPT_SRC
      script.async = true
      script.defer = true
      document.body.appendChild(script)
    }
    script.addEventListener('load', render)

    return () => {
      cancelled = true
      script.removeEventListener('load', render)
    }
  }, [clientId, onCredential])

  if (!clientId) {
    // No client id configured (see .env.example) -- silently omit the button rather than
    // rendering something broken.
    return null
  }

  return <div ref={containerRef} aria-label="Sign in with Google" />
}
