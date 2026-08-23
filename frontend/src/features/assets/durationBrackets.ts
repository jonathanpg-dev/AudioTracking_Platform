import { formatDuration } from '@/utils/format'

// 30-second duration brackets for the asset duration filter -- 0:00-0:30, 0:30-1:00, ... up to a
// final open-ended "10:00+" bracket. The filter picks a min bracket and a max bracket (inclusive
// range of brackets), which AssetsPage translates into minDurationSeconds/maxDurationSeconds.
const BRACKET_COUNT = 20 // 0:00 through 10:00, in 30s steps

export interface DurationBracket {
  index: number
  label: string
  minSeconds: number
  maxSeconds: number | null // null = no upper bound (only the last bracket)
}

export const DURATION_BRACKETS: DurationBracket[] = [
  ...Array.from({ length: BRACKET_COUNT }, (_, i) => ({
    index: i,
    label: `${formatDuration(i * 30)}–${formatDuration((i + 1) * 30)}`,
    minSeconds: i * 30,
    maxSeconds: (i + 1) * 30 - 1,
  })),
  {
    index: BRACKET_COUNT,
    label: `${formatDuration(BRACKET_COUNT * 30)}+`,
    minSeconds: BRACKET_COUNT * 30,
    maxSeconds: null,
  },
]
