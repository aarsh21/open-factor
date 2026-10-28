import { createApp, type VideoIndex } from './app'
import { createStorage } from './storage'
import { serve } from '@hono/node-server'
import { readFile } from 'node:fs/promises'
import { setTimeout } from 'node:timers/promises'

const index = JSON.parse(await readFile(new URL('../video-index.json', import.meta.url), 'utf8')) as VideoIndex
if (index.count !== 1831) throw new Error('Expected the complete exercise video index')
const storage = createStorage(process.env)
const app = createApp(index, storage.store, process.env.MEDIA_ADMIN_TOKEN ?? '')
let initialized = false
for (let attempt = 0; attempt < 30; attempt++) {
  try { await storage.initialize(); initialized = true; break }
  catch { await setTimeout(1000) }
}
if (!initialized) throw new Error('Media bucket initialization failed')
// The Node adapter preserves fixed Content-Length on S3 streams. Bun's native
// stream server uses chunked transfer, which prevents Cloudflare cached byte ranges.
const server = serve({
  hostname: '0.0.0.0', port: Number(process.env.PORT ?? 3000), fetch: app.fetch,
})
if ('requestTimeout' in server) server.requestTimeout = 120_000
console.info(`Media service ready on port ${process.env.PORT ?? 3000}; ${index.count} indexed videos`)
for (const signal of ['SIGTERM', 'SIGINT'] as const) process.on(signal, () => {
  server.close(() => {
    storage.close()
    process.exit(0)
  })
})
