// Purely decorative waveform bars -- the one recurring visual motif tying the brand together
// (login brand panel, sidebar wordmark). Fixed, deterministic bar heights rather than random per
// render, so it doesn't jitter on re-render and stays identical between server/client.
const BAR_HEIGHTS = [0.3, 0.55, 0.85, 0.5, 1, 0.65, 0.4, 0.75, 0.35, 0.9, 0.5, 0.3, 0.6, 0.4, 0.2]

export function Waveform({ className }: { className?: string }) {
  return (
    <div className={className} aria-hidden="true">
      <div className="flex h-full items-end gap-[3px]">
        {BAR_HEIGHTS.map((height, index) => (
          <span
            key={index}
            className="flex-1 rounded-full bg-current"
            style={{ height: `${Math.round(height * 100)}%`, opacity: 0.35 + height * 0.65 }}
          />
        ))}
      </div>
    </div>
  )
}
