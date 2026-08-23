import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { NavLinks } from './NavLinks'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'
import { mockUser } from '@/test/handlers'

const BASE = 'http://localhost:8080'

function signIn() {
  window.localStorage.setItem('audiotracking.token', 'fake-jwt-token')
}

describe('NavLinks', () => {
  it('shows the full nav, with no "Become a creator too" button, for a regular account', async () => {
    signIn() // default /users/me handler: isClientOnly false

    renderWithProviders(<NavLinks />)

    expect(await screen.findByRole('link', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Projects' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /become a creator too/i })).not.toBeInTheDocument()
  })

  it('shows only "Projects" plus "Become a creator too" for a client-only account', async () => {
    server.use(
      http.get(`${BASE}/api/v1/users/me`, () => HttpResponse.json({ ...mockUser, isClientOnly: true, isLinkedAsClient: true })),
    )
    signIn()

    renderWithProviders(<NavLinks />)

    // Wait for the button first -- it only ever renders once /users/me has actually resolved as
    // client-only, unlike a bare "Projects" link match, which would also match FULL_NAV_ITEMS'
    // own (differently-targeted) Projects entry during the brief pre-load render.
    expect(await screen.findByRole('button', { name: /become a creator too/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Projects' })).toHaveAttribute('href', '/client-projects')
    expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument()
  })

  it('clicking "Become a creator too" unlocks the full nav', async () => {
    // A stateful pair of handlers standing in for the real backend: GET reflects whatever the
    // POST last set, so this actually proves NavLinks reacts to the refetched value rather than
    // to the mutation's own response body.
    let unlocked = false
    server.use(
      http.get(`${BASE}/api/v1/users/me`, () =>
        HttpResponse.json({ ...mockUser, isClientOnly: !unlocked, isLinkedAsClient: true }),
      ),
      http.post(`${BASE}/api/v1/users/me/creator-mode`, () => {
        unlocked = true
        return HttpResponse.json({ ...mockUser, isClientOnly: false, isLinkedAsClient: true })
      }),
    )
    signIn()
    const user = userEvent.setup()

    renderWithProviders(<NavLinks />)

    await user.click(await screen.findByRole('button', { name: /become a creator too/i }))

    expect(await screen.findByRole('link', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /become a creator too/i })).not.toBeInTheDocument()
  })

  it('shows an inline error and keeps the client-only nav if unlocking fails', async () => {
    server.use(
      http.get(`${BASE}/api/v1/users/me`, () => HttpResponse.json({ ...mockUser, isClientOnly: true, isLinkedAsClient: true })),
      http.post(`${BASE}/api/v1/users/me/creator-mode`, () => HttpResponse.json({ message: 'Something went wrong' }, { status: 500 })),
    )
    signIn()
    const user = userEvent.setup()

    renderWithProviders(<NavLinks />)
    await user.click(await screen.findByRole('button', { name: /become a creator too/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument()
  })
})
