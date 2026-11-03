import { afterEach, describe, expect, test } from 'bun:test'
import { createStorage } from '../src/storage'

const filename = '0123456789abcdef0123456789abcdef_a.mp4'
const bytes = new Uint8Array([0, 0, 0, 12, 102, 116, 121, 112, 105, 115, 111, 109])
const checksum = 'a'.repeat(64)
const cleanups: Array<() => void> = []

afterEach(() => {
  for (const cleanup of cleanups.splice(0)) cleanup()
})

function fixture() {
  const state = {
    bucket: false,
    raceOnCreate: false,
    bucketStatus: 0,
    objectStatus: 200,
    contentLength: bytes.length,
    uploaded: new Uint8Array(),
    uploadHeaders: new Headers(),
  }
  const requests: Array<{ method: string; path: string; range: string | null }> = []
  const server = Bun.serve({
    hostname: '127.0.0.1',
    port: 0,
    async fetch(request) {
      const path = new URL(request.url).pathname
      requests.push({ method: request.method, path, range: request.headers.get('Range') })
      if (path === '/media' || path === '/media/') {
        if (state.bucketStatus) return new Response(null, { status: state.bucketStatus })
        if (request.method === 'PUT') {
          state.bucket = true
          return new Response(null, { status: state.raceOnCreate ? 409 : 200 })
        }
        return new Response(null, { status: state.bucket ? 200 : 404 })
      }
      if (state.objectStatus !== 200) return new Response(null, { status: state.objectStatus })
      if (request.method === 'PUT') {
        state.uploaded = new Uint8Array(await request.arrayBuffer())
        state.uploadHeaders = new Headers(request.headers)
        return new Response(null, { status: 200 })
      }
      const headers = { 'Content-Length': String(state.contentLength), 'x-amz-meta-sha256': checksum }
      return new Response(request.method === 'HEAD' ? null : bytes, { status: 200, headers })
    },
  })
  cleanups.push(() => server.stop(true))
  const storage = createStorage({
    S3_ENDPOINT: `http://127.0.0.1:${server.port}`,
    S3_ACCESS_KEY: 'test-access-key',
    S3_SECRET_KEY: 'test-secret-key',
    S3_BUCKET: 'media',
  })
  cleanups.push(storage.close)
  return { storage, state, requests }
}

describe('S3 storage adapter', () => {
  test('rejects missing and placeholder configuration before contacting storage', () => {
    const env = { S3_ENDPOINT: 'http://127.0.0.1', S3_ACCESS_KEY: 'access', S3_SECRET_KEY: 'secret', S3_BUCKET: 'media' }
    for (const key of Object.keys(env)) {
      expect(() => createStorage({ ...env, [key]: '' })).toThrow(`Set ${key}`)
      expect(() => createStorage({ ...env, [key]: 'replace-example' })).toThrow(`Set ${key}`)
    }
  })
  test('initialization creates only a missing bucket and is idempotent', async () => {
    const { storage, requests } = fixture()
    await storage.initialize()
    await storage.initialize()
    expect(requests.map(request => request.method)).toEqual(['HEAD', 'PUT', 'HEAD'])
  })
  test('initialization tolerates another process creating the bucket first', async () => {
    const { storage, state } = fixture()
    state.raceOnCreate = true
    await storage.initialize()
    expect(await storage.store.ready()).toBe(true)
  })
  test('readiness fails closed and initialization preserves non-missing failures', async () => {
    const { storage, state, requests } = fixture()
    state.bucketStatus = 403
    expect(await storage.store.ready()).toBe(false)
    await expect(storage.initialize()).rejects.toThrow()
    expect(requests.every(request => request.method === 'HEAD')).toBe(true)
  })
  test('head preserves object length and checksum and maps only 404 to missing', async () => {
    const { storage, state } = fixture()
    expect(await storage.store.head(filename)).toEqual({ bytes: bytes.length, sha256: checksum })
    state.objectStatus = 404
    expect(await storage.store.head(filename)).toBeNull()
    state.objectStatus = 403
    await expect(storage.store.head(filename)).rejects.toThrow()
  })
  test('get requests the exact range and returns a readable byte stream', async () => {
    const { storage, requests } = fixture()
    const body = await storage.store.get(filename, 0, bytes.length - 1)
    expect(new Uint8Array(await new Response(body).arrayBuffer())).toEqual(bytes)
    expect(requests[0]).toEqual({ method: 'GET', path: `/media/videos/${filename}`, range: 'bytes=0-11' })
  })
  test('get rejects storage that ignores the requested range length', async () => {
    const { storage } = fixture()
    // The fixture returns the whole object instead of the requested suffix.
    await expect(storage.store.get(filename, 1, bytes.length - 1)).rejects.toThrow('Storage returned an invalid media length')
  })
  test('put sends exact bytes, integrity metadata and immutable MP4 headers', async () => {
    const { storage, state } = fixture()
    await storage.store.put(filename, bytes, checksum)
    expect(state.uploaded).toEqual(bytes)
    expect(state.uploadHeaders.get('Content-Length')).toBe(String(bytes.length))
    expect(state.uploadHeaders.get('Content-Type')).toBe('video/mp4')
    expect(state.uploadHeaders.get('Cache-Control')).toBe('public, max-age=31536000, immutable')
    expect(state.uploadHeaders.get('x-amz-meta-sha256')).toBe(checksum)
  })
})
