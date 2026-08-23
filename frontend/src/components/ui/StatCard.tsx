import { Card, CardContent } from './Card'
import { Skeleton } from './Skeleton'

interface StatCardProps {
  label: string
  value: string | number
  isLoading?: boolean
}

export function StatCard({ label, value, isLoading }: StatCardProps) {
  return (
    <Card>
      <CardContent>
        <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</p>
        {isLoading ? <Skeleton className="mt-2 h-7 w-16" /> : <p className="mt-1 text-2xl font-semibold text-ink">{value}</p>}
      </CardContent>
    </Card>
  )
}
