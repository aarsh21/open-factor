import { describe, expect, test } from 'bun:test'
import { createHash } from 'node:crypto'
import { createApp, MAX_VIDEO_BYTES, parseRange, parseVideoIndex, validateIndex, type VideoIndex, type VideoStore } from '../src/app'

const filename = '0123456789abcdef0123456789abcdef_a.mp4'
const data = Buffer.concat([Buffer.from([0, 0, 0, 24]), Buffer.from('ftyp'), Buffer.from('isom0123456789abcdefghijklmnopqrstuvwxyz')])
const record = { bytes: data.length, sha256: createHash('sha256').update(data).digest('hex') }
const index: VideoIndex = { schema: 1, count: 1, videos: { [filename]: record } }
const token = 'test-only-token-that-is-longer-than-thirty-two-characters'

function fixture(present = true) {
  let stored = present ? data : null
  let puts = 0
  let storageReady = true
  let headOverride: typeof record | undefined
  const store: VideoStore = {
    async head() { return stored ? (headOverride ?? record) : null },
    async get(_name, start, end) {
      if (!stored) throw new Error('Cannot read an absent fixture object')
      const chunk = stored.subarray(start, end + 1)
      return new ReadableStream({ start(controller) { controller.enqueue(chunk); controller.close() } })
    },
    async put(_name, bytes) { stored = Buffer.from(bytes); puts++ },
    async ready() { return storageReady },
  }
  return { app: createApp(index, store, token), puts: () => puts, failReady: () => { storageReady = false }, corrupt: () => { headOverride = { ...record, bytes: 9 } } }
}

describe('media index validation', () => {
  test('parses valid data and rejects malformed JSON with a deployment diagnostic', () => {
    expect(parseVideoIndex(JSON.stringify(index))).toEqual(index)
    expect(() => parseVideoIndex('{')).toThrow('Invalid media index JSON')
  })
  test('rejects invalid top-level and videos shapes without leaking TypeErrors', () => {
    for (const value of [null, [], 1, 'index', {}, { ...index, schema: 2 },
      { ...index, count: '1' }, { ...index, count: 0 }, { ...index, count: 1.5 },
      { ...index, count: 2 }, { ...index, videos: null }, { ...index, videos: [] }]) {
      expect(() => validateIndex(value)).toThrow('Invalid media index')
    }
  })
  test('rejects invalid records, sizes, checksums and filenames', () => {
    for (const value of [null, [], 'record', {}, { ...record, bytes: '12' },
      { ...record, bytes: 11 }, { ...record, bytes: MAX_VIDEO_BYTES + 1 },
      { ...record, bytes: 12.5 }, { ...record, bytes: Number.POSITIVE_INFINITY },
      { ...record, sha256: null }, { ...record, sha256: 123 },
      { ...record, sha256: record.sha256.toUpperCase() }]) {
      expect(() => validateIndex({ ...index, videos: { [filename]: value } })).toThrow('Invalid indexed media record')
    }
    expect(() => validateIndex({ ...index, videos: { '../video.mp4': record } })).toThrow('Invalid indexed media record')
  })
})

describe('public immutable video HTTP contract', () => {
  test('GET streams exact bytes, SHA ETag, MIME, immutable caching', async () => {
    const { app } = fixture()
    const result = await app.request(`/videos/${filename}`)
    expect(result.status).toBe(200)
    expect(Buffer.from(await result.arrayBuffer())).toEqual(data)
    expect(result.headers.get('Content-Length')).toBe(String(data.length))
    expect(result.headers.get('Content-Type')).toBe('video/mp4')
    expect(result.headers.get('Cache-Control')).toContain('immutable')
    expect(result.headers.get('ETag')).toBe(`"sha256-${record.sha256}"`)
  })
  test('HEAD describes bytes without fetching a body and ignores Range', async () => {
    const { app } = fixture()
    const result = await app.request(`/videos/${filename}`, { method: 'HEAD', headers: { Range: 'bytes=0-3' } })
    expect(result.status).toBe(200)
    expect(result.headers.get('Content-Length')).toBe(String(data.length))
    expect(await result.text()).toBe('')
  })
  test('closed, open-ended, clamped and suffix ranges return exact 206 bodies', async () => {
    const { app } = fixture()
    for (const [range, start, end] of [['bytes=2-9', 2, 9], ['bytes=8-', 8, data.length - 1], ['bytes=0-99999', 0, data.length - 1], ['bytes=-5', data.length - 5, data.length - 1], ['bytes=-99999', 0, data.length - 1]] as const) {
      const result = await app.request(`/videos/${filename}`, { headers: { Range: range } })
      expect(result.status).toBe(206)
      expect(result.headers.get('Content-Range')).toBe(`bytes ${start}-${end}/${data.length}`)
      expect(Buffer.from(await result.arrayBuffer())).toEqual(data.subarray(start, end + 1))
    }
  })
  test('bad, multipart, zero suffix and unsatisfiable ranges return 416', async () => {
    const { app } = fixture()
    for (const range of ['bytes=999999999999999999999-', 'bytes=-0', 'bytes=9-2', 'bytes=0-1,4-5', 'bytes=-', 'potatoes=1-2']) {
      const result = await app.request(`/videos/${filename}`, { headers: { Range: range } })
      expect(result.status).toBe(416)
      expect(result.headers.get('Content-Range')).toBe(`bytes */${data.length}`)
    }
  })
  test('If-Range mismatch returns full body and matching ETag returns 206', async () => {
    const { app } = fixture()
    expect((await app.request(`/videos/${filename}`, { headers: { Range: 'bytes=0-1', 'If-Range': '"old"' } })).status).toBe(200)
    expect((await app.request(`/videos/${filename}`, { headers: { Range: 'bytes=0-1', 'If-Range': `"sha256-${record.sha256}"` } })).status).toBe(206)
  })
  test('conditional ETags support weak/list/wildcard matches and return empty 304', async () => {
    const { app } = fixture()
    for (const condition of ['*', `W/"sha256-${record.sha256}"`, `"old", "sha256-${record.sha256}"`]) {
      const result = await app.request(`/videos/${filename}`, { headers: { 'If-None-Match': condition } })
      expect(result.status).toBe(304)
      expect(await result.text()).toBe('')
    }
  })
  test('unknown filenames, traversal and absent objects remain 404 and uncached', async () => {
    const { app } = fixture(false)
    for (const path of [`/videos/${filename}`, `/videos/${'a'.repeat(32)}_a.mp4`, '/videos/..%2F.env', '/.env']) {
      const result = await app.request(path)
      expect(result.status).toBe(404)
      expect(result.headers.get('Cache-Control')).toBe('no-store')
    }
  })
  test('storage metadata corruption fails closed; ready endpoint reflects bucket', async () => {
    const value = fixture()
    value.corrupt()
    expect((await value.app.request(`/videos/${filename}`)).status).toBe(503)
    expect((await value.app.request('/readyz')).status).toBe(200)
    value.failReady()
    expect((await value.app.request('/readyz')).status).toBe(503)
  })
})

describe('authenticated catalog-bound uploads', () => {
  test('anonymous and incorrect tokens cannot upload', async () => {
    const value = fixture(false)
    for (const auth of ['', 'Bearer wrong']) expect((await value.app.request(`/admin/videos/${filename}`, { method: 'PUT', headers: { Authorization: auth }, body: data })).status).toBe(401)
    expect(value.puts()).toBe(0)
  })
  test('valid bytes are stored only after matching exact checksum and length', async () => {
    const value = fixture(false)
    const result = await value.app.request(`/admin/videos/${filename}`, { method: 'PUT', headers: { Authorization: `Bearer ${token}` }, body: data })
    expect(result.status).toBe(201)
    expect((await result.json()).sha256).toBe(record.sha256)
    expect(value.puts()).toBe(1)
  })
  test('mismatch, too-large declared body, and unknown paths do not reach S3', async () => {
    const value = fixture(false)
    const headers = { Authorization: `Bearer ${token}` }
    expect((await value.app.request(`/admin/videos/${filename}`, { method: 'PUT', headers, body: Buffer.alloc(data.length) })).status).toBe(422)
    expect((await value.app.request(`/admin/videos/${filename}`, { method: 'PUT', headers: { ...headers, 'Content-Length': '33554433' }, body: 'x' })).status).toBe(413)
    expect((await value.app.request(`/admin/videos/${'b'.repeat(32)}_a.mp4`, { method: 'PUT', headers, body: data })).status).toBe(404)
    expect(value.puts()).toBe(0)
  })
  test('unbounded/chunked bodies are stopped at the indexed size', async () => {
    const value = fixture(false)
    const body = new ReadableStream({ start(controller) { controller.enqueue(new Uint8Array(data.length + 1)); controller.close() } })
    const request = new Request(`http://localhost/admin/videos/${filename}`, { method: 'PUT', headers: { Authorization: `Bearer ${token}` }, body, duplex: 'half' } as RequestInit)
    expect((await value.app.fetch(request)).status).toBe(413)
    expect(value.puts()).toBe(0)
  })
  test('range parser handles arbitrarily large ends without integer rounding', () => {
    expect(parseRange('bytes=0-99999999999999999999999999', 100)).toEqual({ start: 0, end: 99 })
    expect(parseRange('bytes=-99999999999999999999999999', 100)).toEqual({ start: 0, end: 99 })
  })
})
