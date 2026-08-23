import { describe, expect, it } from 'vitest'
import { getNavItems } from './navItems'

describe('getNavItems', () => {
  it('gives a regular account (owns things, not linked as a client anywhere) the full nav', () => {
    const items = getNavItems({ isClientOnly: false, isLinkedAsClient: false })
    expect(items.map((i) => i.label)).toEqual(['Dashboard', 'Assets', 'Projects', 'Collections', 'Clients', 'Analytics'])
  })

  it('gives a dual-role account (owns things AND is a linked client somewhere) the full nav plus Client Projects', () => {
    const items = getNavItems({ isClientOnly: false, isLinkedAsClient: true })
    expect(items.map((i) => i.label)).toEqual(['Dashboard', 'Assets', 'Projects', 'Collections', 'Clients', 'Analytics', 'Client Projects'])
    expect(items.at(-1)).toEqual({ label: 'Client Projects', to: '/client-projects' })
  })

  it('gives a client-only account (owns nothing, only linked as a client) a single simplified Projects entry', () => {
    const items = getNavItems({ isClientOnly: true, isLinkedAsClient: true })
    expect(items).toEqual([{ label: 'Projects', to: '/client-projects' }])
  })
})
