import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useActivity } from './hooks'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { Select } from '@/components/ui/Input'
import { Skeleton } from '@/components/ui/Skeleton'
import { ErrorState } from '@/components/ui/ErrorState'
import { getErrorMessage } from '@/utils/errors'

// Answers "how active was my workspace over time" -- every day in range gets a bar (backend
// omits zero-count days entirely; gaps are filled in here so the x-axis stays continuous).
export function ActivityChart() {
  const [days, setDays] = useState(30)
  const activity = useActivity({ days })

  if (activity.isPending) {
    return <Skeleton className="h-72 w-full" />
  }

  if (activity.isError) {
    return <ErrorState message={getErrorMessage(activity.error)} />
  }

  const bucketsByDate = new Map(activity.data.buckets.map((bucket) => [bucket.date, bucket.count]))
  const chartData: { date: string; count: number }[] = []
  const cursor = new Date(activity.data.from)
  const end = new Date(activity.data.to)
  while (cursor <= end) {
    const iso = cursor.toISOString().slice(0, 10)
    chartData.push({
      date: cursor.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      count: bucketsByDate.get(iso) ?? 0,
    })
    cursor.setDate(cursor.getDate() + 1)
  }

  const change = activity.data.changeFromPreviousPeriodPercent

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle>Activity over time</CardTitle>
          <p className="mt-1 text-sm text-ink-muted">
            {activity.data.totalEvents} events
            {change !== null && (
              <span className={change >= 0 ? 'text-success' : 'text-danger'}>
                {' '}
                ({change >= 0 ? '+' : ''}
                {change.toFixed(0)}% vs. previous period)
              </span>
            )}
          </p>
        </div>
        <Select value={days} onChange={(e) => setDays(Number(e.target.value))} className="w-32" aria-label="Time range">
          <option value={7}>Last 7 days</option>
          <option value={30}>Last 30 days</option>
        </Select>
      </CardHeader>
      <CardContent>
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
              <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} width={28} />
              <Tooltip cursor={{ fill: '#f8fafc' }} />
              <Bar dataKey="count" name="Events" fill="#4f46e5" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}
