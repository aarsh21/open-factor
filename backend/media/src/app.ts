import { createHash, timingSafeEqual } from 'node:crypto'
import { Hono } from 'hono'

export const MAX_VIDEO_BYTES = 32 * 1024 * 1024
export const VIDEO_NAME = /^[0-9a-f]{32}_[a-z]\.mp4$/
export type VideoRecord = { bytes: number; sha256: string }
export type VideoIndex = { schema: number; count: number; videos: Record<string, VideoRecord> }
export type VideoStore = {
  head(name: string): Promise<VideoRecord | null>
  get(name: string, start: number, end: number): Promise<ReadableStream<Uint8Array>>
  put(name: string, bytes: Uint8Array, sha256: string): Promise<void>
  ready(): Promise<boolean>
}

export function validateIndex(index: VideoIndex): void {
  if (index.schema !== 1 || index.count !== Object.keys(index.videos).length || index.count < 1) throw new Error('Invalid media index')
  for (const [name, entry] of Object.entries(index.videos)) {
    if (!VIDEO_NAME.test(name) || !Number.isSafeInteger(entry.bytes) || entry.bytes < 12 || entry.bytes > MAX_VIDEO_BYTES || !/^[0-9a-f]{64}$/.test(entry.sha256)) throw new Error('Invalid indexed media record')
  }
}

/** One RFC 9110 byte range; unsafe integers and multipart requests are rejected. */
export function parseRange(value: string, bytes: number): { start: number; end: number } | null {
  const match = /^bytes=(\d*)-(\d*)$/.exec(value.trim())
  if (!match || (!match[1] && !match[2])) return null
  const length = BigInt(bytes)
  if (!match[1]) {
    const suffix = BigInt(match[2])
    return suffix > 0n ? { start: Number(suffix >= length ? 0n : length - suffix), end: bytes - 1 } : null
  }
  const start = BigInt(match[1])
  const requestedEnd = match[2] ? BigInt(match[2]) : length - 1n
  if (start >= length || requestedEnd < start) return null
  return { start: Number(start), end: Number(requestedEnd >= length ? length - 1n : requestedEnd) }
}

function etag(record: VideoRecord): string { return `"sha256-${record.sha256}"` }
function matchesEtag(value: string | undefined, tag: string): boolean {
  return value?.split(',').some(item => item.trim() === '*' || item.trim().replace(/^W\//, '') === tag) ?? false
}

export function createApp(index: VideoIndex, store: VideoStore, adminToken: string) {
  validateIndex(index)
  if (adminToken.length < 32 || adminToken.startsWith('replace-')) throw new Error('A strong admin token is required')
  const expectedAuth = createHash('sha256').update(`Bearer ${adminToken}`).digest()
  const app = new Hono()
  let activeUploads = 0
  app.use('*', async (c, next) => {
    c.header('X-Content-Type-Options', 'nosniff')
    c.header('Cache-Control', 'no-store')
    await next()
  })
  // Do not log headers, environment, S3 errors or stack traces containing endpoints/credentials.
  app.onError((_error, c) => c.json({ error: 'Media storage is unavailable' }, 503))
  app.notFound(c => c.json({ error: 'Not found' }, 404))
  app.get('/healthz', c => c.json({ status: 'ok', catalog_count: index.count }))
  app.get('/readyz', async c => {
    const ready = await store.ready().catch(() => false)
    return c.json({ status: ready ? 'ready' : 'unavailable', catalog_count: index.count }, ready ? 200 : 503)
  })
  app.on(['GET', 'HEAD'], '/videos/:filename', async c => {
    const name = c.req.param('filename')
    const expected = VIDEO_NAME.test(name) ? index.videos[name] : undefined
    if (!expected) return c.json({ error: 'Not found' }, 404)
    const actual = await store.head(name)
    if (!actual) return c.json({ error: 'Not found' }, 404)
    if (actual.bytes !== expected.bytes || actual.sha256 !== expected.sha256) return c.json({ error: 'Media integrity check failed' }, 503)
    const tag = etag(expected)
    const headers = new Headers({
      'Content-Type': 'video/mp4', 'ETag': tag, 'Accept-Ranges': 'bytes',
      'Cache-Control': 'public, max-age=31536000, immutable',
      'X-Content-SHA256': expected.sha256, 'X-Content-Type-Options': 'nosniff',
      'Access-Control-Allow-Origin': '*', 'Access-Control-Expose-Headers': 'ETag, Content-Range, Content-Length, X-Content-SHA256',
    })
    if (matchesEtag(c.req.header('If-None-Match'), tag)) return new Response(null, { status: 304, headers })
    let start = 0, end = expected.bytes - 1, status = 200
    const range = c.req.header('Range')
    const ifRange = c.req.header('If-Range')
    // RFC 9110 applies Range to GET; a HEAD always describes the complete representation.
    if (c.req.method === 'GET' && range && (!ifRange || ifRange === tag)) {
      const parsed = parseRange(range, expected.bytes)
      if (!parsed) {
        headers.set('Content-Range', `bytes */${expected.bytes}`)
        headers.set('Cache-Control', 'no-store')
        return new Response(null, { status: 416, headers })
      }
      ;({ start, end } = parsed)
      status = 206
      headers.set('Content-Range', `bytes ${start}-${end}/${expected.bytes}`)
    }
    headers.set('Content-Length', String(end - start + 1))
    if (c.req.method === 'HEAD') return new Response(null, { status, headers })
    return new Response(await store.get(name, start, end), { status, headers })
  })
  app.put('/admin/videos/:filename', async c => {
    const auth = c.req.header('Authorization') ?? ''
    if (auth.length > 1024 || !timingSafeEqual(createHash('sha256').update(auth).digest(), expectedAuth))
      return c.json({ error: 'Unauthorized' }, 401, { 'WWW-Authenticate': 'Bearer' })
    const name = c.req.param('filename')
    const expected = VIDEO_NAME.test(name) ? index.videos[name] : undefined
    if (!expected) return c.json({ error: 'Not found' }, 404)
    const declared = c.req.header('Content-Length')
    if (declared && (!/^\d+$/.test(declared) || BigInt(declared) > BigInt(MAX_VIDEO_BYTES))) return c.json({ error: 'Video is too large' }, 413)
    if (declared && Number(declared) !== expected.bytes) return c.json({ error: 'Video length does not match the catalog' }, 400)
    if (!c.req.raw.body) return c.json({ error: 'Video body is required' }, 400)
    if (activeUploads >= 4) return c.json({ error: 'Upload capacity reached' }, 429, { 'Retry-After': '2' })
    activeUploads++
    const reader = c.req.raw.body.getReader()
    try {
      const chunks: Uint8Array[] = []
      let received = 0
      const hash = createHash('sha256')
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        received += value.byteLength
        if (received > MAX_VIDEO_BYTES || received > expected.bytes) {
          await reader.cancel()
          return c.json({ error: 'Video body exceeds its expected length' }, 413)
        }
        hash.update(value)
        chunks.push(value)
      }
      const actualHash = hash.digest('hex')
      if (received !== expected.bytes || actualHash !== expected.sha256) return c.json({ error: 'Video checksum or length does not match the catalog' }, 422)
      const bytes = new Uint8Array(received)
      let offset = 0
      for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength }
      if (Buffer.from(bytes.subarray(4, 8)).toString('ascii') !== 'ftyp') return c.json({ error: 'Expected an MP4 video' }, 422)
      await store.put(name, bytes, actualHash)
      return c.json({ filename: name, bytes: received, sha256: actualHash }, 201)
    } finally {
      reader.releaseLock()
      activeUploads--
    }
  })
  return app
}
