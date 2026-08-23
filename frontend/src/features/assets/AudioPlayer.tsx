import { useRef, useState, type ChangeEvent } from 'react'
import { useAssetFileAccess } from './hooks'
import { formatDuration } from '@/utils/format'
import { getErrorMessage } from '@/utils/errors'
import { Spinner } from '@/components/ui/Spinner'
import { InlineError } from '@/components/ui/ErrorState'

interface AudioPlayerProps {
  assetId: string
  hasAudioFile: boolean
}

// Fetches a fresh short-lived presigned URL from the backend the first time playback is
// requested (never before, never cached beyond this component's own state) and hands it
// straight to a plain <audio> element -- the browser streams from R2 directly from there. See
// AssetService#getFileAccessUrl on the backend for why `download` is a separate, explicit intent.
export function AudioPlayer({ assetId, hasAudioFile }: AudioPlayerProps) {
  const audioRef = useRef<HTMLAudioElement>(null)
  const pendingPlayRef = useRef(false)
  const [src, setSrc] = useState<string | null>(null)
  const [isPlaying, setIsPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)

  const fileAccess = useAssetFileAccess(assetId)

  async function handlePlayPause() {
    if (!hasAudioFile) return
    const audio = audioRef.current
    if (!src) {
      pendingPlayRef.current = true
      const response = await fileAccess.mutateAsync(false) // false = "play" intent
      setSrc(response.url)
      return
    }
    if (!audio) return
    if (isPlaying) {
      audio.pause()
    } else {
      void audio.play()
    }
  }

  async function handleDownload() {
    if (!hasAudioFile) return
    const response = await fileAccess.mutateAsync(true) // true = "download" intent
    // The URL points directly at R2, cross-origin from this app -- opening it in a new tab is
    // the honest, reliable option (a same-origin-only <a download> wouldn't force a download for
    // a cross-origin URL in most browsers anyway).
    window.open(response.url, '_blank', 'noopener,noreferrer')
  }

  function handleLoadedMetadata() {
    const audio = audioRef.current
    if (!audio) return
    setDuration(audio.duration)
    if (pendingPlayRef.current) {
      pendingPlayRef.current = false
      void audio.play()
    }
  }

  function handleSeek(event: ChangeEvent<HTMLInputElement>) {
    const audio = audioRef.current
    if (!audio) return
    const time = Number(event.target.value)
    audio.currentTime = time
    setCurrentTime(time)
  }

  function handleVolumeChange(event: ChangeEvent<HTMLInputElement>) {
    if (audioRef.current) {
      audioRef.current.volume = Number(event.target.value)
    }
  }

  if (!hasAudioFile) {
    return <p className="text-sm text-ink-muted">No audio file uploaded yet.</p>
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border border-border bg-surface-muted p-3">
      <audio
        ref={audioRef}
        src={src ?? undefined}
        onLoadedMetadata={handleLoadedMetadata}
        onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onEnded={() => setIsPlaying(false)}
        className="hidden"
      />

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => void handlePlayPause()}
          disabled={fileAccess.isPending}
          aria-label={isPlaying ? 'Pause' : 'Play'}
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent text-on-accent hover:bg-accent-hover disabled:opacity-60"
        >
          {fileAccess.isPending ? <Spinner size="sm" /> : isPlaying ? <PauseIcon /> : <PlayIcon />}
        </button>

        <span className="w-10 shrink-0 text-xs tabular-nums text-ink-muted">{formatDuration(currentTime)}</span>
        <input
          type="range"
          aria-label="Seek"
          min={0}
          max={duration || 0}
          step={0.1}
          value={currentTime}
          onChange={handleSeek}
          disabled={!src}
          className="h-1.5 flex-1 accent-accent"
        />
        <span className="w-10 shrink-0 text-xs tabular-nums text-ink-muted">{formatDuration(duration)}</span>

        <label className="flex items-center gap-1.5">
          <span className="sr-only">Volume</span>
          <VolumeIcon />
          <input
            type="range"
            aria-label="Volume"
            min={0}
            max={1}
            step={0.05}
            defaultValue={1}
            onChange={handleVolumeChange}
            className="h-1.5 w-16 accent-accent"
          />
        </label>

        <button
          type="button"
          onClick={() => void handleDownload()}
          disabled={fileAccess.isPending}
          className="shrink-0 text-xs font-medium text-accent hover:underline disabled:opacity-60"
        >
          Download
        </button>
      </div>

      {fileAccess.isError && <InlineError message={getErrorMessage(fileAccess.error)} />}
    </div>
  )
}

function PlayIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor" aria-hidden="true">
      <path d="M2 1.5v11l10-5.5-10-5.5Z" />
    </svg>
  )
}

function PauseIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor" aria-hidden="true">
      <rect x="2" y="1.5" width="3.5" height="11" />
      <rect x="8.5" y="1.5" width="3.5" height="11" />
    </svg>
  )
}

function VolumeIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M2 6h2.5l3.5-3v10l-3.5-3H2V6Z" fill="currentColor" />
      <path d="M11 5.5a3.5 3.5 0 0 1 0 5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  )
}
