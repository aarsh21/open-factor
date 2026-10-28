# Exercise video service

Hono/Node streams the original exercise videos from a private RustFS bucket. The Android app uses `https://<media-domain>/v1/videos/<32-hex-id>_<view>.mp4`. TLS terminates at Dokploy's HTTPS proxy; the API listens on internal port 3000. Static previews and anatomy remain packaged in the app. `/videos/` remains a compatibility route; `/v1/` is the canonical cache namespace.

The checked-in index contains exactly 1,831 filenames, byte lengths and SHA-256 values. No video, extracted photo, instruction text or secret is committed. Regenerate it only from the matching local catalog and original files:

```sh
python3 backend/media/generate_index.py --reference openfactor/app/src/main/assets/reference
```

Read routes support GET/HEAD, single byte ranges (including suffixes), 206/416, ETag/304, If-Range and one-year immutable caching. HEAD describes the full object even when Range is supplied. Unknown names and unuploaded objects return 404 without immutable caching. Successful responses expose `X-Content-SHA256`. Filenames are immutable: authenticated uploads must match the indexed hash and byte length before any S3 write, and are limited to 32 MiB and four concurrent uploads. The production HTTP adapter runs on Node 24 to preserve fixed Content-Length on streamed S3 bodies for CDN byte-range support; Bun builds and runs unit tests.

`GET /healthz` checks the process; `GET /readyz` checks the private bucket. Startup creates the bucket if absent. RustFS has no anonymous bucket policy. Only the API is routed publicly; S3 and its console must stay private. The console is disabled. Production Compose publishes no host ports.

## Dokploy

Create a Compose application from this directory (`backend/media/compose.yml` in the repository). Build context is the compose file's directory, using its pinned Dockerfile. Route the chosen HTTPS media hostname to service `api`, container port **3000**. Keep its `/videos/` prefix intact. Supply these private environment variables through Dokploy:

| Variable | Value |
| --- | --- |
| `RUSTFS_ACCESS_KEY` | New random S3 access key |
| `RUSTFS_SECRET_KEY` | New random S3 secret |
| `MEDIA_ADMIN_TOKEN` | New random bearer token, at least 32 characters |
| `S3_BUCKET` | `open-factor-videos` |

The API receives internal `S3_ENDPOINT=http://rustfs:9000`, `S3_REGION=us-east-1`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` and `PORT=3000` from Compose. Keep the persistent `exercise-videos` volume across deployments. Back up this volume and the private credentials. Avoid exposing RustFS itself or enabling a public bucket policy. The API image and RustFS image are pinned to verified registry digests.

## Upload

`tooling/upload_exercise_media.py` first verifies local source hashes. It is a dry run unless `--upload` is supplied. Token input is `MEDIA_ADMIN_TOKEN`, `--token-file` or `--env-file`; credentials never belong in command arguments. The script uses up to four workers, retries transient network/HTTP failures, skips matching existing objects and verifies each new upload by HEAD plus full SHA-256 readback from canonical `/v1/videos/` URLs. Add `--verify-existing` for a complete readback audit of previously uploaded files. Failure reports retain successful transfers and exit nonzero; reruns safely resume.

With `--report`, every completed result is atomically checkpointed with its verified byte length and SHA-256. Resume with `--resume-report` pointing to that receipt; only full-body verified records from the same origin, `/v1/` namespace and exact index are accepted. For bounded agent/CI sessions, add `--batch-size 300` and repeat with the same receipt until `remaining_files` is zero. An interrupted process cannot discard already checkpointed verification.

```sh
python3 tooling/upload_exercise_media.py \
  --base-url https://factor-media.example.com \
  --videos openfactor/app/src/main/assets/reference/videos \
  --env-file /private/path/media.env

# Add --upload only after the HTTPS service is ready.
```

## Local verification

```sh
cd backend/media
bun install --frozen-lockfile
bun test
bun run typecheck
python3 test/uploader_test.py
python3 create_local_env.py  # creates ignored .env with mode 0600; refuses overwrite
docker compose -p open-factor-media-integration -f compose.yml -f compose.local.yml up -d --build --wait
python3 test/local_integration.py
docker compose -p open-factor-media-integration -f compose.yml -f compose.local.yml down
```

The local override binds API 18310 and S3 18311 to `127.0.0.1`. Integration uploads one exact original clip and checks full-body SHA, HEAD, ranges, ETags, unauthorized uploads and unsigned S3 denial. Its sanitized report is written under ignored `.local/`. Shut down only this named test project. Do not add `--volumes` to a production teardown.

Implementation follows the [RustFS Docker guide](https://docs.rustfs.com/en/installation/container/docker), [Hono Bun guide](https://hono.dev/docs/getting-started/bun), and [S3 GetObject contract](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html).
