import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useRegister, useLogin } from '@/features/auth/hooks'
import { ThemeSwitcher } from '@/features/theme/ThemeSwitcher'
import { Button } from '@/components/ui/Button'
import { Input, Label } from '@/components/ui/Input'
import { InlineError } from '@/components/ui/ErrorState'
import { Waveform } from '@/components/ui/Waveform'
import { getErrorMessage } from '@/utils/errors'

export function RegisterPage() {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()

  const register = useRegister()
  // Registration and login are separate backend endpoints (POST /auth/register never returns a
  // token) -- log the new account in immediately after so the user doesn't have to re-enter
  // their credentials a second time.
  const login = useLogin()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    register.mutate(
      { username, email, password },
      {
        onSuccess: () => {
          login.mutate(
            { username, password },
            { onSuccess: () => navigate('/dashboard', { replace: true }) },
          )
        },
      },
    )
  }

  const isSubmitting = register.isPending || login.isPending

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Brand panel -- see LoginPage for the twin of this. */}
      <div className="brand-gradient relative hidden overflow-hidden lg:flex lg:flex-col lg:justify-between lg:p-10 lg:text-white">
        <span className="font-display text-lg font-semibold tracking-tight">AudioTracking Platform</span>
        <div>
          <Waveform className="h-16 w-full max-w-md text-white" />
          <p className="mt-6 max-w-md font-display text-3xl font-semibold leading-tight">
            Your catalog, your credits, your clients — one workspace.
          </p>
          <p className="mt-3 max-w-sm text-sm text-white/80">
            Track every asset from first sketch to final delivery.
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
          <h1 className="mt-1 font-display text-xl font-semibold text-ink lg:mt-0">Create your account</h1>
          <p className="mt-1 text-sm text-ink-muted">Start organizing your audio workspace.</p>

          <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
            <div>
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                autoComplete="username"
                required
                minLength={3}
                maxLength={30}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>

            {register.isError && <InlineError message={getErrorMessage(register.error)} />}
            {login.isError && <InlineError message="Account created — please sign in from the login page." />}

            <Button type="submit" className="w-full" isLoading={isSubmitting}>
              Create account
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-ink-muted">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-accent hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
