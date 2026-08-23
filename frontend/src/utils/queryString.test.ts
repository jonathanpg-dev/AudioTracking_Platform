import { describe, expect, it } from 'vitest'
import { toQueryString } from './queryString'

describe('toQueryString', () => {
  it('returns an empty string for no present values', () => {
    expect(toQueryString({ a: undefined, b: null, c: '' })).toBe('')
  })

  it('skips undefined/null/empty-string values but keeps everything else', () => {
    expect(toQueryString({ a: 1, b: undefined, c: 'x', d: null, e: '', f: false })).toBe('?a=1&c=x&f=false')
  })

  it('repeats the key once per array element', () => {
    expect(toQueryString({ tagIds: ['a', 'b', 'c'] })).toBe('?tagIds=a&tagIds=b&tagIds=c')
  })

  it('omits an empty array entirely, same as an absent scalar', () => {
    expect(toQueryString({ tagIds: [] as string[], assetType: 'BEAT' })).toBe('?assetType=BEAT')
  })

  it('filters out empty/undefined/null items within an array without dropping the rest', () => {
    expect(toQueryString({ tagIds: ['a', undefined, '', 'b'] as (string | undefined)[] })).toBe('?tagIds=a&tagIds=b')
  })

  // A filter value is arbitrary user input (e.g. the musicalKey/audioFormat text fields on
  // AssetsPage) reaching a URL -- it must never be able to inject extra query params or break out
  // of its own key's value via unescaped &, =, #, or similar. URLSearchParams (used internally)
  // percent-encodes automatically; these are regression guards against ever swapping that out for
  // manual string concatenation.
  it('percent-encodes special characters instead of letting them alter the query structure', () => {
    const result = toQueryString({ musicalKey: 'A&injected=1#x' })
    expect(result).toBe('?musicalKey=A%26injected%3D1%23x')
    // Decoding it back must reproduce exactly the original value, and only one param exists.
    const params = new URLSearchParams(result)
    expect([...params.keys()]).toEqual(['musicalKey'])
    expect(params.get('musicalKey')).toBe('A&injected=1#x')
  })

  it('percent-encodes special characters within array elements the same way', () => {
    const result = toQueryString({ tagIds: ['<script>alert(1)</script>'] })
    const params = new URLSearchParams(result)
    expect([...params.keys()]).toEqual(['tagIds'])
    expect(params.get('tagIds')).toBe('<script>alert(1)</script>')
  })
})
