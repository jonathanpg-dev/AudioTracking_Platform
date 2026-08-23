import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-2 bg-surface-muted text-center">
      <p className="text-lg font-semibold text-ink">Page not found</p>
      <p className="text-sm text-ink-muted">The page you're looking for doesn't exist.</p>
      <Link to="/dashboard" className="mt-2 text-sm font-medium text-accent hover:underline">
        Back to dashboard
      </Link>
    </div>
  )
}
