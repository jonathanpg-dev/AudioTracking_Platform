import { describe, expect, it } from 'vitest'
import { DURATION_BRACKETS } from './durationBrackets'

describe('DURATION_BRACKETS', () => {
  it('starts at 0 seconds with no gaps between consecutive brackets', () => {
    expect(DURATION_BRACKETS[0].minSeconds).toBe(0)
    for (let i = 1; i < DURATION_BRACKETS.length; i++) {
      const previous = DURATION_BRACKETS[i - 1]
      const current = DURATION_BRACKETS[i]
      // Every bracket but the last has a fixed upper bound -- the next one must pick up exactly
      // where it left off, or a duration could fall into neither (or both) brackets.
      expect(previous.maxSeconds).not.toBeNull()
      expect(current.minSeconds).toBe((previous.maxSeconds as number) + 1)
    }
  })

  it('is exactly 30 seconds wide for every bracket except the last, open-ended one', () => {
    for (const bracket of DURATION_BRACKETS.slice(0, -1)) {
      expect((bracket.maxSeconds as number) - bracket.minSeconds).toBe(29)
    }
    expect(DURATION_BRACKETS[DURATION_BRACKETS.length - 1].maxSeconds).toBeNull()
  })
})
