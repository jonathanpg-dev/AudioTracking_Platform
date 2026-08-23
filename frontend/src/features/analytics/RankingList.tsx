import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { Skeleton } from '@/components/ui/Skeleton'

interface RankingEntry {
  id: string
  label: string | null
  count: number
}

interface RankingListProps {
  title: string
  entries?: RankingEntry[]
  isLoading?: boolean
  emptyMessage: string
  countLabel: string
  onEntryClick?: (id: string) => void
}

// The shared shape behind "most played assets", "most downloaded assets", "most active
// projects", and "most shared projects" -- one component instead of four near-identical lists.
// A null label means the underlying Asset/Project has since been deleted -- the historical count
// still shows, just without a name to click through to (see docs/analytics.md on the backend).
export function RankingList({ title, entries, isLoading, emptyMessage, countLabel, onEntryClick }: RankingListProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="space-y-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-6 w-full" />
            ))}
          </div>
        )}
        {!isLoading && (!entries || entries.length === 0) && <p className="text-sm text-ink-muted">{emptyMessage}</p>}
        {!isLoading && entries && entries.length > 0 && (
          <ol className="space-y-2">
            {entries.map((entry, index) => (
              <li key={entry.id} className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 truncate">
                  <span className="text-ink-subtle">{index + 1}.</span>
                  {entry.label === null ? (
                    <span className="italic text-ink-subtle">Deleted</span>
                  ) : onEntryClick ? (
                    <button onClick={() => onEntryClick(entry.id)} className="truncate text-ink hover:text-accent hover:underline">
                      {entry.label}
                    </button>
                  ) : (
                    <span className="truncate text-ink">{entry.label}</span>
                  )}
                </span>
                <span className="shrink-0 text-ink-muted">
                  {entry.count} {countLabel}
                </span>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}
