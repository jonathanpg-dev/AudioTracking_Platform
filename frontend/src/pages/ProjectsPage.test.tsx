import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { ProjectsPage } from './ProjectsPage'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'
import { mockProject } from '@/test/handlers'

const BASE = 'http://localhost:8080'

// GET /projects now includes Projects shared with the caller, not just owned ones (see
// docs/collaboration.md) -- these confirm the list renders a role badge for shared projects,
// distinguishing them from the (much more common) case of the user's own projects.
describe('ProjectsPage', () => {
  it('shows a role badge for a project shared with the current user', async () => {
    const sharedProject = { ...mockProject, id: 'project-2', name: 'Shared With Me', myRole: 'VIEW' as const }
    server.use(http.get(`${BASE}/api/v1/projects`, () => HttpResponse.json([sharedProject])))

    renderWithProviders(<ProjectsPage />)

    await screen.findByText('Shared With Me')
    expect(screen.getByText('VIEW')).toBeInTheDocument()
  })

  it('does not show a role badge for a project the user owns', async () => {
    const ownedProject = { ...mockProject, id: 'project-1', name: 'My Own Project', myRole: 'OWNER' as const }
    server.use(http.get(`${BASE}/api/v1/projects`, () => HttpResponse.json([ownedProject])))

    renderWithProviders(<ProjectsPage />)

    await screen.findByText('My Own Project')
    expect(screen.queryByText('OWNER')).not.toBeInTheDocument()
  })

  it('renders a mix of owned and shared projects correctly in the same list', async () => {
    const owned = { ...mockProject, id: 'project-1', name: 'My Own Project', myRole: 'OWNER' as const }
    const shared = { ...mockProject, id: 'project-2', name: 'Editable Shared Project', myRole: 'EDIT' as const }
    server.use(http.get(`${BASE}/api/v1/projects`, () => HttpResponse.json([owned, shared])))

    renderWithProviders(<ProjectsPage />)

    await screen.findByText('My Own Project')
    await screen.findByText('Editable Shared Project')
    expect(screen.getByText('EDIT')).toBeInTheDocument()
    expect(screen.queryByText('OWNER')).not.toBeInTheDocument()
  })
})
