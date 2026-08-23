import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CollaboratorsPanel } from './CollaboratorsPanel'
import { renderWithProviders } from '@/test/renderWithProviders'

// The core "never show a collaborator a control the backend would reject anyway" requirement --
// myRole is passed in exactly as ProjectResponse.myRole would provide it, never derived locally.
describe('CollaboratorsPanel', () => {
  it('shows management controls to the project owner', async () => {
    renderWithProviders(<CollaboratorsPanel projectId="project-1" myRole="OWNER" />)

    expect(await screen.findByLabelText(/invite by email/i)).toBeInTheDocument()
  })

  it('hides management controls from a VIEW collaborator', () => {
    renderWithProviders(<CollaboratorsPanel projectId="project-1" myRole="VIEW" />)

    expect(screen.getByText(/only the project owner can manage collaborators/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/invite by email/i)).not.toBeInTheDocument()
  })

  it('hides management controls from an EDIT collaborator', () => {
    renderWithProviders(<CollaboratorsPanel projectId="project-1" myRole="EDIT" />)

    expect(screen.getByText(/only the project owner can manage collaborators/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/invite by email/i)).not.toBeInTheDocument()
  })
})
