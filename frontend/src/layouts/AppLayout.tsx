import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import * as RadixDialog from '@radix-ui/react-dialog'
import { NavLinks } from './NavLinks'
import { UserMenu } from './UserMenu'
import { ThemeSwitcher } from '@/features/theme/ThemeSwitcher'
import { Waveform } from '@/components/ui/Waveform'

// The consistent authenticated shell every protected page renders inside: a fixed sidebar on
// larger screens, a slide-in drawer (reusing Radix Dialog's own focus-trap/Escape handling) on
// small ones, and a topbar carrying the mobile menu toggle + user menu.
export function AppLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  return (
    <div className="min-h-screen bg-surface-muted lg:flex">
      <aside className="hidden w-60 shrink-0 border-r border-border bg-surface lg:flex lg:flex-col">
        <div className="px-4 py-5">
          <span className="font-display text-sm font-semibold tracking-tight text-ink">AudioTracking Platform</span>
          <Waveform className="mt-2 h-4 w-16 text-accent" />
        </div>
        <div className="flex-1 overflow-y-auto px-3">
          <NavLinks />
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center justify-between gap-3 border-b border-border bg-surface px-4">
          <button
            type="button"
            className="rounded-md p-2 text-ink-muted hover:bg-surface-muted lg:hidden"
            aria-label="Open navigation menu"
            onClick={() => setMobileNavOpen(true)}
          >
            <MenuIcon />
          </button>
          <span className="font-display text-sm font-semibold text-ink lg:hidden">AudioTracking Platform</span>
          <div className="ml-auto flex items-center gap-2">
            <ThemeSwitcher />
            <UserMenu />
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6">
          <Outlet />
        </main>
      </div>

      <RadixDialog.Root open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
        <RadixDialog.Portal>
          <RadixDialog.Overlay className="fixed inset-0 z-40 bg-ink/40 lg:hidden" />
          <RadixDialog.Content className="fixed inset-y-0 left-0 z-50 w-64 bg-surface p-4 shadow-lg lg:hidden">
            <RadixDialog.Title className="mb-4 text-sm font-semibold text-ink">Navigation</RadixDialog.Title>
            <NavLinks onNavigate={() => setMobileNavOpen(false)} />
          </RadixDialog.Content>
        </RadixDialog.Portal>
      </RadixDialog.Root>
    </div>
  )
}

function MenuIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}
