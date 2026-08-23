import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { LoginPage } from './LoginPage'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'

function TestApp() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/dashboard" element={<div>Dashboard Content</div>} />
    </Routes>
  )
}

describe('LoginPage', () => {
  it('signs the user in and navigates to the dashboard on success', async () => {
    const user = userEvent.setup()
    renderWithProviders(<TestApp />, { route: '/login' })

    await user.type(screen.getByLabelText(/username/i), 'testuser')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText('Dashboard Content')).toBeInTheDocument()
  })

  it('shows the backend error message when login fails', async () => {
    server.use(
      http.post('http://localhost:8080/api/v1/auth/login', () =>
        HttpResponse.json(
          { timestamp: '', status: 401, error: 'Unauthorized', message: 'Invalid username or password', fieldErrors: null },
          { status: 401 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderWithProviders(<TestApp />, { route: '/login' })

    await user.type(screen.getByLabelText(/username/i), 'testuser')
    await user.type(screen.getByLabelText(/password/i), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText('Invalid username or password')).toBeInTheDocument()
    // Must NOT have navigated away on failure.
    expect(screen.queryByText('Dashboard Content')).not.toBeInTheDocument()
  })
})
