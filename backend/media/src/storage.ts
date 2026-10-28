import { S3Client, HeadObjectCommand, GetObjectCommand, PutObjectCommand, HeadBucketCommand, CreateBucketCommand } from '@aws-sdk/client-s3'
import type { VideoStore } from './app'

function missing(error: unknown): boolean {
  return (error as { $metadata?: { httpStatusCode?: number } })?.$metadata?.httpStatusCode === 404
}

export function createStorage(env: Record<string, string | undefined>) {
  const required = ['S3_ENDPOINT', 'S3_ACCESS_KEY', 'S3_SECRET_KEY', 'S3_BUCKET'] as const
  for (const key of required) if (!env[key] || env[key]!.startsWith('replace-')) throw new Error(`Set ${key}`)
  const bucket = env.S3_BUCKET!
  const client = new S3Client({
    endpoint: env.S3_ENDPOINT, region: env.S3_REGION ?? 'us-east-1', forcePathStyle: true,
    credentials: { accessKeyId: env.S3_ACCESS_KEY!, secretAccessKey: env.S3_SECRET_KEY! },
    requestChecksumCalculation: 'WHEN_REQUIRED', responseChecksumValidation: 'WHEN_REQUIRED',
    requestHandler: { connectionTimeout: 3000, requestTimeout: 30000 }, maxAttempts: 3,
  })
  const store: VideoStore = {
    async head(name) {
      try {
        const object = await client.send(new HeadObjectCommand({ Bucket: bucket, Key: `videos/${name}` }))
        return { bytes: object.ContentLength ?? -1, sha256: object.Metadata?.sha256 ?? '' }
      } catch (error) { if (missing(error)) return null; throw error }
    },
    async get(name, start, end) {
      const object = await client.send(new GetObjectCommand({ Bucket: bucket, Key: `videos/${name}`, Range: `bytes=${start}-${end}` }))
      if (!object.Body || object.ContentLength !== end - start + 1) throw new Error('Storage returned an invalid media length')
      return object.Body.transformToWebStream() as ReadableStream<Uint8Array>
    },
    async put(name, bytes, sha256) {
      await client.send(new PutObjectCommand({ Bucket: bucket, Key: `videos/${name}`, Body: bytes,
        ContentLength: bytes.byteLength, ContentType: 'video/mp4', CacheControl: 'public, max-age=31536000, immutable', Metadata: { sha256 } }))
    },
    async ready() {
      try { await client.send(new HeadBucketCommand({ Bucket: bucket })); return true } catch { return false }
    },
  }
  return { store, async initialize() {
    try { await client.send(new HeadBucketCommand({ Bucket: bucket })) }
    catch (error) {
      if (!missing(error)) throw error
      try { await client.send(new CreateBucketCommand({ Bucket: bucket })) }
      catch (createError) {
        if (!(await store.ready())) throw createError
      }
    }
    // No public bucket policy is installed. Only authenticated S3 clients can read the objects.
  }, close: () => client.destroy() }
}
