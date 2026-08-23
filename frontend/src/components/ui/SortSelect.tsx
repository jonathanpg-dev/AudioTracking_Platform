import { Select } from './Input'
import type { SortDirection, SortField, SortParams } from '@/types/sort'

const SORT_OPTIONS: { key: string; label: string; sortBy: SortField; sortDir: SortDirection }[] = [
  { key: 'createdAt:desc', label: 'Newest added', sortBy: 'createdAt', sortDir: 'desc' },
  { key: 'createdAt:asc', label: 'Oldest added', sortBy: 'createdAt', sortDir: 'asc' },
  { key: 'updatedAt:desc', label: 'Recently modified', sortBy: 'updatedAt', sortDir: 'desc' },
  { key: 'updatedAt:asc', label: 'Least recently modified', sortBy: 'updatedAt', sortDir: 'asc' },
]

const DEFAULT_KEY = SORT_OPTIONS[0].key

// A single "order by" dropdown shared by AssetsPage/ProjectsPage/CollectionsPage -- combines
// sortBy+sortDir into one choice (rather than two separate selects) since every combination the
// backend supports maps to one natural phrase ("Newest added", "Recently modified", ...).
export function SortSelect({ value, onChange }: { value: SortParams; onChange: (next: SortParams) => void }) {
  const key = value.sortBy && value.sortDir ? `${value.sortBy}:${value.sortDir}` : DEFAULT_KEY

  function handleChange(nextKey: string) {
    const option = SORT_OPTIONS.find((candidate) => candidate.key === nextKey) ?? SORT_OPTIONS[0]
    onChange({ sortBy: option.sortBy, sortDir: option.sortDir })
  }

  return (
    <Select
      value={key}
      onChange={(e) => handleChange(e.target.value)}
      className="sm:max-w-[200px]"
      aria-label="Sort by"
    >
      {SORT_OPTIONS.map((option) => (
        <option key={option.key} value={option.key}>
          {option.label}
        </option>
      ))}
    </Select>
  )
}
