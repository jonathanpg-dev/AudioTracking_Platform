import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DurationInput } from './DurationInput'

describe('DurationInput', () => {
  it('seeds its segments from an initial total-seconds value', () => {
    render(<DurationInput id="d" value={3725} onChange={vi.fn()} />) // 1h 02m 05s

    expect(screen.getByLabelText('Hours')).toHaveValue('1')
    expect(screen.getByLabelText('Minutes')).toHaveValue('02')
    expect(screen.getByLabelText('Seconds')).toHaveValue('05')
  })

  it('renders empty segments for a null value', () => {
    render(<DurationInput id="d" value={null} onChange={vi.fn()} />)

    expect(screen.getByLabelText('Hours')).toHaveValue('')
    expect(screen.getByLabelText('Minutes')).toHaveValue('')
    expect(screen.getByLabelText('Seconds')).toHaveValue('')
  })

  it('combines hours, minutes, and seconds into a total on every keystroke', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<DurationInput id="d" value={null} onChange={onChange} />)

    await user.type(screen.getByLabelText('Hours'), '1')
    await user.type(screen.getByLabelText('Minutes'), '30')
    await user.type(screen.getByLabelText('Seconds'), '45')

    // 1h + 30m + 45s
    expect(onChange).toHaveBeenLastCalledWith(1 * 3600 + 30 * 60 + 45)
  })

  it('ignores non-digit input and caps at 2 digits for minutes/seconds', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<DurationInput id="d" value={null} onChange={onChange} />)

    await user.type(screen.getByLabelText('Seconds'), 'ab1c2d3')

    expect(screen.getByLabelText('Seconds')).toHaveValue('12')
    expect(onChange).toHaveBeenLastCalledWith(12)
  })

  it('clamps minutes/seconds to 59 and pads to 2 digits on blur', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<DurationInput id="d" value={null} onChange={onChange} />)

    await user.type(screen.getByLabelText('Minutes'), '75')
    await user.tab() // blur

    expect(screen.getByLabelText('Minutes')).toHaveValue('59')
    expect(onChange).toHaveBeenLastCalledWith(59 * 60)
  })

  it('reports null once every segment is cleared', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<DurationInput id="d" value={90} onChange={onChange} />)

    await user.clear(screen.getByLabelText('Minutes'))
    await user.clear(screen.getByLabelText('Seconds'))

    expect(onChange).toHaveBeenLastCalledWith(null)
  })
})
