import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { AudioPlayer } from './AudioPlayer'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'

describe('AudioPlayer', () => {
  it('shows a message and no playback controls when there is no audio file', () => {
    renderWithProviders(<AudioPlayer assetId="asset-1" hasAudioFile={false} />)

    expect(screen.getByText(/no audio file uploaded yet/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /play/i })).not.toBeInTheDocument()
  })

  it('fetches a presigned URL and loads it into the player when Play is clicked', async () => {
    server.use(
      http.get('http://localhost:8080/api/v1/assets/asset-1/file', ({ request }) => {
        expect(new URL(request.url).searchParams.get('download')).toBe('false')
        return HttpResponse.json({ url: 'https://r2.example.com/signed?sig=abc', expiresAt: '2026-01-01T00:15:00Z' })
      }),
    )
    const user = userEvent.setup()
    renderWithProviders(<AudioPlayer assetId="asset-1" hasAudioFile />)

    await user.click(screen.getByRole('button', { name: /play/i }))

    await waitFor(() => {
      const audio = document.querySelector('audio')
      expect(audio?.getAttribute('src')).toBe('https://r2.example.com/signed?sig=abc')
    })
  })

  it('requests a download-intent URL (not play) when Download is clicked', async () => {
    server.use(
      http.get('http://localhost:8080/api/v1/assets/asset-1/file', ({ request }) => {
        expect(new URL(request.url).searchParams.get('download')).toBe('true')
        return HttpResponse.json({ url: 'https://r2.example.com/signed?sig=download', expiresAt: '2026-01-01T00:15:00Z' })
      }),
    )
    // window.open isn't implemented in jsdom -- stub it so the download handler doesn't throw.
    window.open = () => null

    const user = userEvent.setup()
    renderWithProviders(<AudioPlayer assetId="asset-1" hasAudioFile />)

    await user.click(screen.getByRole('button', { name: /download/i }))
  })

  // Regression test for the "still plays the old file after replacing it" bug: AudioPlayer
  // fetches its presigned URL once and caches it in state for the component's lifetime, so
  // nothing inside AudioPlayer itself ever notices a file replace. The actual fix lives in the
  // caller (AssetDetailPage keys <AudioPlayer> on audioFormat+fileSizeBytes, which change on
  // every upload) -- this proves that mechanism actually does what it's relied on to do: a
  // changed key must produce a fresh instance that fetches a new URL, not the stale cached one.
  it('fetches a fresh URL instead of the stale cached one once remounted with a new key (simulating a file replace)', async () => {
    let callCount = 0
    server.use(
      http.get('http://localhost:8080/api/v1/assets/asset-1/file', () => {
        callCount++
        return HttpResponse.json({
          url: callCount === 1 ? 'https://r2.example.com/old-file' : 'https://r2.example.com/new-file',
          expiresAt: '2026-01-01T00:15:00Z',
        })
      }),
    )
    const user = userEvent.setup()
    const { rerender } = renderWithProviders(<AudioPlayer key="wav-1000" assetId="asset-1" hasAudioFile />)

    await user.click(screen.getByRole('button', { name: /play/i }))
    await waitFor(() =>
      expect(document.querySelector('audio')?.getAttribute('src')).toBe('https://r2.example.com/old-file'),
    )

    // Same assetId/hasAudioFile as before -- only the key (standing in for the file's identity
    // changing) differs, exactly as AssetDetailPage now does after a successful replace.
    rerender(<AudioPlayer key="mp3-2000" assetId="asset-1" hasAudioFile />)

    expect(document.querySelector('audio')?.getAttribute('src')).toBeFalsy() // fresh instance, nothing fetched yet
    await user.click(screen.getByRole('button', { name: /play/i }))
    await waitFor(() =>
      expect(document.querySelector('audio')?.getAttribute('src')).toBe('https://r2.example.com/new-file'),
    )
  })
})
