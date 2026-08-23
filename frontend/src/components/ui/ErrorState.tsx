import { Button } from './Button'

interface ErrorStateProps {
  title?: string
  message: string
  onRetry?: () => void
}

// Full-page/full-section error state -- used when a query fails and there's nothing else useful
// to show. For a 403 specifically, callers pass title="You don't have permission" (see the
// "For 403: display an appropriate message" requirement).
export function ErrorState({ title = 'Something went wrong', message, onRetry }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center justify-center rounded-lg border border-border bg-surface px-6 py-16 text-center"
    >
      <p className="text-sm font-medium text-ink">{title}</p>
      <p className="mt-1 max-w-sm text-sm text-ink-muted">{message}</p>
      {onRetry && (
        <div className="mt-4">
          <Button variant="secondary" onClick={onRetry}>
            Try again
          </Button>
        </div>
      )}
    </div>
  )
}

// A smaller, inline version for forms -- doesn't take over the whole page.
export function InlineError({ message }: { message: string }) {
  return (
    <div role="alert" className="rounded-md border border-danger-soft bg-danger-soft px-3 py-2 text-sm text-danger">
      {message}
    </div>
  )
}
