import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import App from './App'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'
import { mockUser } from '@/test/handlers'

const BASE = 'http://localhost:8080'

function signIn() {
  window.localStorage.setItem('audiotracking.token', 'fake-jwt-token')
}

// Regression coverage for ClientOnlyGate in App.tsx: a client-only account's simplified UI (see
// navItems.ts) would be purely cosmetic if the producer-facing pages were still reachable by
// typing the URL directly -- this proves the redirect actually happens, not just that the nav
// link is hidden.
describe('ClientOnlyGate', () => {
  it('redirects a client-only account away from Dashboard to Client Projects', async () => {
    server.use(
      http.get(`${BASE}/api/v1/users/me`, () => HttpResponse.json({ ...mockUser, isClientOnly: true, isLinkedAsClient: true })),
      http.get(`${BASE}/api/v1/projects/as-client`, () => HttpResponse.json([])),
    )
    signIn()

    renderWithProviders(<App />, { route: '/dashboard' })

    expect(await screen.findByText(/no projects shared with you yet/i)).toBeInTheDocument()
  })

  it('lets a client-only account stay on a project detail page', async () => {
    server.use(
      http.get(`${BASE}/api/v1/users/me`, () => HttpResponse.json({ ...mockUser, isClientOnly: true, isLinkedAsClient: true })),
    )
    signIn()

    renderWithProviders(<App />, { route: '/projects/project-1' })

    expect(await screen.findByText('Test Project')).toBeInTheDocument()
  })

  it('does not redirect a regular (non-client-only) account away from Dashboard', async () => {
    signIn() // default /users/me handler returns isClientOnly: false

    renderWithProviders(<App />, { route: '/dashboard' })

    expect(await screen.findByText(/welcome back/i)).toBeInTheDocument()
    expect(screen.queryByText(/no projects shared with you yet/i)).not.toBeInTheDocument()
  })
})
