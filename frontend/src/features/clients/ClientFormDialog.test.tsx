import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { ClientFormDialog } from './ClientFormDialog'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'

describe('ClientFormDialog', () => {
  it('submits the form and calls onSaved with the created client', async () => {
    server.use(
      http.post('http://localhost:8080/api/v1/clients', async ({ request }) => {
        const body = (await request.json()) as { name: string }
        return HttpResponse.json(
          { id: 'client-1', name: body.name, email: null, company: null, notes: null, createdAt: '', updatedAt: '' },
          { status: 201 },
        )
      }),
    )
    const onSaved = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<ClientFormDialog open onOpenChange={() => {}} onSaved={onSaved} />)

    await user.type(screen.getByLabelText(/^name/i), 'John Smith')
    await user.click(screen.getByRole('button', { name: /add client/i }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ name: 'John Smith' })))
  })

  it('disables the submit button while the request is in flight', async () => {
    server.use(
      http.post(
        'http://localhost:8080/api/v1/clients',
        () => new Promise(() => {}), // never resolves -- keeps the mutation pending
      ),
    )
    const user = userEvent.setup()
    renderWithProviders(<ClientFormDialog open onOpenChange={() => {}} />)

    await user.type(screen.getByLabelText(/^name/i), 'Jane Doe')
    await user.click(screen.getByRole('button', { name: /add client/i }))

    expect(await screen.findByRole('button', { name: /add client/i })).toBeDisabled()
  })

  it('requires a name before the browser allows submission', () => {
    renderWithProviders(<ClientFormDialog open onOpenChange={() => {}} />)

    expect(screen.getByLabelText(/^name/i)).toBeRequired()
  })
})
