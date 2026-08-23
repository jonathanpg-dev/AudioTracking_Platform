// Builds a "?a=1&b=2" query string, skipping undefined/null/empty-string values entirely rather
// than sending them as literal "undefined" -- every list/filter endpoint in api/ uses this so
// filters stay optional without each call site re-implementing the same skip logic.
//
// An array value (e.g. AssetFilter.tagIds) is sent as a repeated param -- "?tagIds=a&tagIds=b" --
// which is what Spring MVC binds a `List<UUID>` controller parameter from. An empty array is
// skipped entirely, same as undefined/null/'' for a scalar.
//
// Generic over T rather than a plain Record<string, ...> parameter: a closed interface type
// (e.g. AssetFilter) isn't structurally assignable to Record<string, ...> without an explicit
// index signature, and adding one to every filter/query type just to satisfy this helper would
// loosen their typing for no real benefit.
export function toQueryString<T extends object>(params: T): string {
  type Value = string | number | boolean | undefined | null
  const entries = Object.entries(params) as [string, Value | Value[]][]
  const search = new URLSearchParams()
  for (const [key, value] of entries) {
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== undefined && item !== null && item !== '') {
          search.append(key, String(item))
        }
      }
    } else if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const queryString = search.toString()
  return queryString ? `?${queryString}` : ''
}
