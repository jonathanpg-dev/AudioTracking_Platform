import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { renderWithProviders } from '@/test/renderWithProviders'

function TestApp() {
  return (
    <Routes>
      <Route path="/login" element={<div>Login Page</div>} />
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<div>Dashboard Content</div>} />
      </Route>
    </Routes>
  )
}

describe('ProtectedRoute', () => {
  it('redirects an unauthenticated visitor to /login', async () => {
    renderWithProviders(<TestApp />, { route: '/dashboard' })

    expect(await screen.findByText('Login Page')).toBeInTheDocument()
  })

  it('renders the protected content for an authenticated user', async () => {
    window.localStorage.setItem('audiotracking.token', 'fake-jwt-token')

    renderWithProviders(<TestApp />, { route: '/dashboard' })

    expect(await screen.findByText('Dashboard Content')).toBeInTheDocument()
  })
})
