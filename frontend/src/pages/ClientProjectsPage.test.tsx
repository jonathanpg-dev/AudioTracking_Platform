import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { ClientProjectsPage } from './ClientProjectsPage'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'
import { mockProject } from '@/test/handlers'

const BASE = 'http://localhost:8080'

function TestApp() {
  return (
    <Routes>
      <Route path="/client-projects" element={<ClientProjectsPage />} />
    </Routes>
  )
}

describe('ClientProjectsPage', () => {
  it('lists projects from GET /projects/as-client, not the regular projects list', async () => {
    const clientProject = { ...mockProject, id: 'project-9', name: 'Client-Shared Project', myRole: 'CLIENT' as const }
    server.use(
      http.get(`${BASE}/api/v1/projects/as-client`, () => HttpResponse.json([clientProject])),
      http.get(`${BASE}/api/v1/projects`, () => HttpResponse.json([])), // proves this page never reads the regular list
    )

    renderWithProviders(<TestApp />, { route: '/client-projects' })

    expect(await screen.findByText('Client-Shared Project')).toBeInTheDocument()
  })

  it('shows an empty state when nothing has been shared yet', async () => {
    server.use(http.get(`${BASE}/api/v1/projects/as-client`, () => HttpResponse.json([])))

    renderWithProviders(<TestApp />, { route: '/client-projects' })

    expect(await screen.findByText(/no projects shared with you yet/i)).toBeInTheDocument()
  })
})
