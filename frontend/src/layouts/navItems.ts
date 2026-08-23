export interface NavItem {
  label: string
  to: string
}

// The full nav for a regular account -- both the desktop sidebar and the mobile drawer render
// from getNavItems() below, so they can never drift out of sync with each other.
const FULL_NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Assets', to: '/assets' },
  { label: 'Projects', to: '/projects' },
  { label: 'Collections', to: '/collections' },
  { label: 'Clients', to: '/clients' },
  { label: 'Analytics', to: '/analytics' },
]

const CLIENT_PROJECTS_ITEM: NavItem = { label: 'Client Projects', to: '/client-projects' }

// A client-only account (see CurrentUser.isClientOnly) owns nothing of its own -- Dashboard,
// Assets, Collections, Clients, and Analytics would all just be empty for it, so it gets a single
// simplified "Projects" entry pointing at the client-projects list instead of the full nav. A
// dual-role account (owns things AND is linked as a client somewhere) keeps the full nav plus one
// extra entry for its client-side projects.
export function getNavItems({ isClientOnly, isLinkedAsClient }: { isClientOnly: boolean; isLinkedAsClient: boolean }): NavItem[] {
  if (isClientOnly) {
    return [{ label: 'Projects', to: '/client-projects' }]
  }
  return isLinkedAsClient ? [...FULL_NAV_ITEMS, CLIENT_PROJECTS_ITEM] : FULL_NAV_ITEMS
}
