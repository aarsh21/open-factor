import { createApp, MAX_VIDEO_BYTES, type VideoIndex } from './app'
import { createStorage } from './storage'

const index = await Bun.file(new URL('../video-index.json', import.meta.url)).json() as VideoIndex
if (index.count !== 1831) throw new Error('Expected the complete exercise video index')
const storage = createStorage(process.env)
const app = createApp(index, storage.store, process.env.MEDIA_ADMIN_TOKEN ?? '')
let initialized = false
for (let attempt = 0; attempt < 30; attempt++) {
  try { await storage.initialize(); initialized = true; break }
  catch { await Bun.sleep(1000) }
}
if (!initialized) throw new Error('Media bucket initialization failed')
const server = Bun.serve({
  hostname: '0.0.0.0', port: Number(process.env.PORT ?? 3000), fetch: app.fetch,
  maxRequestBodySize: MAX_VIDEO_BYTES, idleTimeout: 60,
})
console.info(`Media service ready on port ${server.port}; ${index.count} indexed videos`)
for (const signal of ['SIGTERM', 'SIGINT'] as const) process.on(signal, async () => {
  await server.stop(false)
  storage.close()
  process.exit(0)
})
