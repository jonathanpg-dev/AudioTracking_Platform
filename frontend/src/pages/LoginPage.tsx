import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useLogin, useGoogleLogin } from '@/features/auth/hooks'
import { GoogleSignInButton } from '@/features/auth/GoogleSignInButton'
import { ThemeSwitcher } from '@/features/theme/ThemeSwitcher'
import { Button } from '@/components/ui/Button'
import { Input, Label } from '@/components/ui/Input'
import { InlineError } from '@/components/ui/ErrorState'
import { Waveform } from '@/components/ui/Waveform'
import { getErrorMessage } from '@/utils/errors'

export function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const redirectState = location.state as { from?: { pathname: string } } | null
  const redirectTo = redirectState?.from?.pathname ?? '/dashboard'

  const login = useLogin()
  const googleLogin = useGoogleLogin()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    login.mutate(
      { username, password },
      { onSuccess: () => navigate(redirectTo, { replace: true }) },
    )
  }

  function handleGoogleCredential(idToken: string) {
    googleLogin.mutate(
      { idToken },
      { onSuccess: () => navigate(redirectTo, { replace: true }) },
    )
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Brand panel -- purely decorative, hidden below lg so the form stays front and center on
          mobile. Uses the two-accent brand gradient so it reads differently per theme. */}
      <div className="brand-gradient relative hidden overflow-hidden lg:flex lg:flex-col lg:justify-between lg:p-10 lg:text-white">
        <span className="font-display text-lg font-semibold tracking-tight">AudioTracking Platform</span>
        <div>
          <Waveform className="h-16 w-full max-w-md text-white" />
          <p className="mt-6 max-w-md font-display text-3xl font-semibold leading-tight">
            Every beat, stem, and session — organized like the studio you wish you had.
          </p>
          <p className="mt-3 max-w-sm text-sm text-white/80">
            Built for producers and composers tracking real catalogs, not just files in a folder.
          </p>
        </div>
        <span className="text-xs text-white/60">&copy; {new Date().getFullYear()}</span>
      </div>

      <div className="relative flex items-center justify-center bg-surface-muted px-4 py-16">
        <div className="absolute right-4 top-4">
          <ThemeSwitcher />
        </div>

        <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 shadow-sm">
          <span className="font-display text-lg font-semibold text-ink lg:hidden">AudioTracking Platform</span>
          <h1 className="mt-1 font-display text-xl font-semibold text-ink lg:mt-0">Welcome back</h1>
          <p className="mt-1 text-sm text-ink-muted">Sign in to your workspace.</p>

          <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
            <div>
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                autoComplete="username"
                required
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>

            {login.isError && <InlineError message={getErrorMessage(login.error)} />}

            <Button type="submit" className="w-full" isLoading={login.isPending}>
              Sign in
            </Button>
          </form>

          <div className="my-5 flex items-center gap-3 text-xs uppercase text-ink-subtle">
            <span className="h-px flex-1 bg-border" />
            or
            <span className="h-px flex-1 bg-border" />
          </div>

          <div className="flex flex-col items-center gap-2">
            <GoogleSignInButton onCredential={handleGoogleCredential} />
            {googleLogin.isError && <InlineError message={getErrorMessage(googleLogin.error)} />}
          </div>

          <p className="mt-6 text-center text-sm text-ink-muted">
            New here?{' '}
            <Link to="/register" className="font-medium text-accent hover:underline">
              Create an account
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
