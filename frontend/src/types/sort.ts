// Mirrors the sortBy/sortDir query params shared by GET /assets, /projects, and /collections
// (see SortParams.java on the backend) -- "order by date added/modified, ascending/descending".
export const SORT_FIELDS = ['createdAt', 'updatedAt'] as const
export type SortField = (typeof SORT_FIELDS)[number]

export const SORT_DIRECTIONS = ['asc', 'desc'] as const
export type SortDirection = (typeof SORT_DIRECTIONS)[number]

export interface SortParams {
  sortBy?: SortField
  sortDir?: SortDirection
}
