import { useState, type ChangeEvent, type FocusEvent } from 'react'
import { cn } from '@/utils/cn'

interface DurationInputProps {
  id: string
  // Total seconds -- read once to seed the three segments below, the same "initial value from
  // props, then locally owned" pattern the rest of AssetFormDialog's fields use (the dialog
  // remounts fresh each time it opens, so this never needs to re-sync after mount).
  value: number | null
  onChange: (totalSeconds: number | null) => void
  className?: string
}

function toSegments(totalSeconds: number | null) {
  if (totalSeconds === null || !Number.isFinite(totalSeconds) || totalSeconds < 0) {
    return { h: '', m: '', s: '' }
  }
  const total = Math.floor(totalSeconds)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  // Leading segments only show once they're non-zero (or a later segment forces them in) --
  // matches how you'd naturally read "1:30" rather than "0:01:30".
  return {
    h: h > 0 ? String(h) : '',
    m: h > 0 || m > 0 ? String(m).padStart(h > 0 ? 2 : 1, '0') : '',
    s: String(s).padStart(2, '0'),
  }
}

function digitsOnly(raw: string, maxLength: number) {
  return raw.replace(/\D/g, '').slice(0, maxLength)
}

// H:MM:SS entry for asset duration -- three digit-only segments composed into one total-seconds
// value, rather than a single free-text field: no ambiguous parsing (is "130" 130 seconds or
// 1:30?) and no separator format to remember. Segments are optional/left-empty-friendly, but
// minutes/seconds clamp to 0-59 and pad to 2 digits once the user leaves the field.
export function DurationInput({ id, value, onChange, className }: DurationInputProps) {
  const initial = toSegments(value)
  const [hours, setHours] = useState(initial.h)
  const [minutes, setMinutes] = useState(initial.m)
  const [seconds, setSeconds] = useState(initial.s)

  function emit(h: string, m: string, s: string) {
    if (!h && !m && !s) {
      onChange(null)
      return
    }
    onChange((Number(h) || 0) * 3600 + (Number(m) || 0) * 60 + (Number(s) || 0))
  }

  function handleChange(segment: 'h' | 'm' | 's', maxLength: number) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      const clean = digitsOnly(event.target.value, maxLength)
      const next = {
        h: segment === 'h' ? clean : hours,
        m: segment === 'm' ? clean : minutes,
        s: segment === 's' ? clean : seconds,
      }
      if (segment === 'h') setHours(clean)
      if (segment === 'm') setMinutes(clean)
      if (segment === 's') setSeconds(clean)
      emit(next.h, next.m, next.s)
    }
  }

  function handleBlurClamp(segment: 'm' | 's') {
    return (event: FocusEvent<HTMLInputElement>) => {
      if (!event.target.value) return
      const padded = Math.min(59, Number(event.target.value)).toString().padStart(2, '0')
      if (segment === 'm') {
        setMinutes(padded)
        emit(hours, padded, seconds)
      } else {
        setSeconds(padded)
        emit(hours, minutes, padded)
      }
    }
  }

  const segmentClass =
    'w-6 min-w-0 shrink-0 border-0 bg-transparent p-0 text-center text-ink placeholder:text-ink-subtle focus:outline-none'

  return (
    <div
      className={cn(
        'flex w-fit items-center gap-1 rounded-md border border-border bg-surface px-3 py-2 text-sm',
        'focus-within:border-accent focus-within:ring-2 focus-within:ring-accent',
        className,
      )}
    >
      <input
        id={id}
        type="text"
        inputMode="numeric"
        placeholder="0"
        aria-label="Hours"
        className={segmentClass}
        value={hours}
        onChange={handleChange('h', 2)}
      />
      <span className="text-ink-subtle" aria-hidden="true">
        :
      </span>
      <input
        type="text"
        inputMode="numeric"
        placeholder="00"
        aria-label="Minutes"
        className={segmentClass}
        value={minutes}
        onChange={handleChange('m', 2)}
        onBlur={handleBlurClamp('m')}
      />
      <span className="text-ink-subtle" aria-hidden="true">
        :
      </span>
      <input
        type="text"
        inputMode="numeric"
        placeholder="00"
        aria-label="Seconds"
        className={segmentClass}
        value={seconds}
        onChange={handleChange('s', 2)}
        onBlur={handleBlurClamp('s')}
      />
    </div>
  )
}
