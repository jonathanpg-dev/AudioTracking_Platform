import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './mswServer'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  window.localStorage.clear() // a signed-in token from one test must never leak into the next
})
afterAll(() => server.close())

// jsdom doesn't implement real media playback -- AudioPlayer calls these directly, so without
// this stub every test touching it fails on "Not implemented: HTMLMediaElement.prototype.play".
window.HTMLMediaElement.prototype.play = () => Promise.resolve()
window.HTMLMediaElement.prototype.pause = () => {}
