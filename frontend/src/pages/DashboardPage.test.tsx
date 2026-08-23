import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { renderWithProviders } from '@/test/renderWithProviders'

// The spec's explicit requirement: no analytics are fabricated in frontend code -- every number
// here must come straight from the (mocked, in this test) backend response.
describe('DashboardPage', () => {
  it('renders real overview numbers from the analytics API', async () => {
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('3')).toBeInTheDocument() // totalAssets from mockOverview
    expect(screen.getByText('5')).toBeInTheDocument() // totalPlays
    expect(screen.getByText('2')).toBeInTheDocument() // totalDownloads
  })
})
