// Node 22+ defines its own inert `localStorage` getter directly on globalThis (it only works
// behind the --localstorage-file flag). Vitest's jsdom environment merges jsdom's window onto
// global, but skips any key that already exists on global unless that key is on a fixed
// whitelist -- and `localStorage` isn't on it. Result: Node's non-functional getter wins and
// jsdom's real, working Storage implementation never gets attached at all.
//
// The fix is a small in-memory Storage implementation installed directly on globalThis, replacing
// Node's inert getter. This is what `window.localStorage` resolves to in every test, since the
// jsdom environment aliases `window` to `global`.
class MemoryStorage implements Storage {
  private store = new Map<string, string>()

  get length(): number {
    return this.store.size
  }

  clear(): void {
    this.store.clear()
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? this.store.get(key)! : null
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null
  }

  removeItem(key: string): void {
    this.store.delete(key)
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value))
  }
}

Object.defineProperty(globalThis, 'localStorage', {
  value: new MemoryStorage(),
  writable: true,
  configurable: true,
})
