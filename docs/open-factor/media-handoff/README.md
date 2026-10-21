# Mac agent handoff: original exercise media

Continue from repository `aarsh21/open-factor`, branch `cmp-rewrite`. This task is original exercise-media acquisition only. The Open Factor app and full reproduction goal remain unfinished.

## Copy-paste prompt

```text
Continue Open Factor's original exercise-media recovery on this MacBook.

Read docs/open-factor/media-handoff/README.md, input-summary.json,
exercise-targets.jsonl, and manifest.schema.json before operating the app.

Use the genuine installed MacroFactor Workouts app and my authorized account.
Discover this Mac's architecture, the actual app bundle ID/version and its own
cache/container paths. Official support is documented for Apple Silicon Macs;
do not assume any MacBook can run the app. Ask me to sign in directly if needed.

Recover ORIGINAL exercise demonstration MP4s and exercise photos. First copy
and validate one original MP4, then continue through the accessible exercise
library and each available camera view using a resumable acquisition ledger.
Normal app navigation may be used to populate its cache. Inspect only this
app's relevant cache/bundle or authorized media responses. Preserve file bytes
and filenames; do not transcode, crop, screen-record, synthesize videos from
photos, or substitute promotional videos. Exclude personal progress photos.

The supplied map has 1,345 exercises, with 1,831 statically reconstructed video
paths for 619 of them. Paths are not verified downloads. Do not guess suffixes
for the other 726 exercises. Record genuinely discovered additional media
separately and explain the mapping evidence. Keep ambiguous files unmapped.

Prefer copying complete files from the app's own cache. Check the source file
is stable, compare source/copy SHA-256, inspect video metadata and fully decode
with ffmpeg before marking a file verified. Do not clear or move source caches.
Use REA for artifact/runtime evidence when available; label ordinary filesystem
observations separately. No source-app patching, integrity spoofing, rooting,
security-check changes or account-token transfer into Open Factor. Keep any
credentials encountered out of logs, manifests, Git and the returned archive.

Save results under inspection/private/mac-media-export/ (Git-ignored), using
the output layout/schema below. Return a private archive with media, the
per-file manifest, checksums, provenance, failures and exact missing coverage.
Do not commit/publish downloaded media or credentials. Do not claim complete
coverage until verified. If access fails, preserve the exact error and partial
results; do not replace originals with screen recordings.

Start with Chin-up if available: exercise ID
1a15c6f170d880b6adcbff83e8bb6b0f, expected selector order c, a, b.
An actual Mac acquisition has not yet been verified; report what works.
```

## Known state

- Android reference: MacroFactor Workouts `com.sbs.train`, version 1.4.1/code 130. The Mac bundle ID/version must be discovered independently.
- Bundled catalog: **1,345 exercises**. Exact photo mapping: **1,831 files for 619 exercises**, plus one unmatched packaged image. These photos were already recovered locally, so a Mac copy may duplicate them.
- `exercise-targets.jsonl` contains all 1,345 IDs/names and the **1,831 expected video filenames for 619 exercises**. The other 726 rows have empty target lists; this does not prove those exercises have no server media.
- Reconstructed bucket: `sbs-diet-app-remote-us-central1`; path shape `videos/{exercise_id}_{suffix}.mp4`. Suffix letters are camera selectors, not recovered human angle labels. Selection order is preferred thumbnail first, then a/b/c ranking.
- **Verified original video files so far: zero.** An anonymous Firebase metadata request and an exact `alt=media` GET both returned 401, invalid App Check. A public Cloud Storage HEAD for that known object returned 403. Do not repeat anonymous bulk requests as though paths were public URLs.
- Actual REA/JADX inspection of the shipped Android client proves separate Auth and App Check token inputs. The Android application's initialization selects Play Integrity. A login token alone does not supply App Check; successful off-device authenticated downloading remains unverified.
- The connected Android phone is **not rooted**, and its private cache is inaccessible to ADB. Recordings were explicitly rejected. No original app files or account credentials have been copied out of that private cache.
- [MacroFactor officially documents Apple Silicon Mac support](https://macrofactor.com/annual-report-2026/). Mac cache location, readability, app installation/login and original-file recovery are still unverified. No browser Workouts client was established by the bounded official-site search.

## Acquisition procedure

1. Read local `AGENTS.md` instructions and verify the repository/branch. Establish macOS version, CPU architecture, available tools and genuine Workouts installation. Use normal app/login UI; the user enters credentials privately. Do not record credential entry.
2. Discover the app's actual bundle identity and its own cache/container directories. Do not assume the Android package name is the Mac bundle ID, hard-code a guessed container, or search unrelated personal data. Inspect app-owned temporary/cache and bundled Flutter exercise assets as applicable. Handle normal macOS folder-access prompts through the user-facing flow.
3. Prove one original MP4 can be copied and validated before starting a large pass. Match its literal ID/suffix to the supplied map, and confirm exercise identity in the app where needed. If cache files use different names, retain their original names and record the evidence supporting any association.
4. Keep a resumable ledger for each exercise and available camera view. Navigate the genuine app normally to request missing demonstrations, wait for stable completed files, then copy them. Do not create workouts, alter programs/gyms/notes, change account data, delete app caches or reinstall the app to collect media. If further installation or security-setting changes seem necessary, stop and explain the concrete requirement.
5. Preserve original bytes. Compare a completed source file's hash before and after copying with the destination hash. Record successful media-response acquisition separately if that is legitimately available instead. Never label screenshots, one-frame MP4s, re-encodes, fixtures or app walkthroughs as original demonstrations.
6. Validate copied videos with `ffprobe` and a full `ffmpeg` decode. For photos, verify the image signature/dimensions and decode when tools are available. Record unavailable validation as pending, not passed. Do not discard all partial results if one file fails.
7. Reconcile actual files with `exercise-targets.jsonl`. Keep version-specific disagreements and newly discovered files explicit. Preserve conflicting filename/hash variants in `unmatched/` or a separate versioned export rather than silently overwriting them.
8. Package the results privately. Keep the input map unchanged. Read the existing importer for compatibility, but leave app implementation, releases and media publication to the parent task.

## Required output

Use `inspection/private/mac-media-export/` and confirm `git check-ignore` covers it before saving data. Return the folder as `open-factor-original-media-YYYY-MM-DD.zip` privately, not as a Git commit or public release.

```text
mac-media-export/
  videos/<exercise_id>_<suffix>.mp4
  photos/<original exercise asset filename>
  unmatched/<unresolved or conflicting original file>
  manifest.jsonl
  coverage.json
  missing.jsonl
  failures.jsonl
  SHA256SUMS
  PROVENANCE.txt
```

- **`manifest.jsonl`:** one record per acquired file, following `manifest.schema.json`. Include exact exercise/suffix association or null, relative path, original filename, SHA-256, byte length, app version, acquisition method, mapping evidence and validation. Video records need codec, dimensions and positive duration. No credential values or URL query tokens.
- **`coverage.json`:** report actual app version, catalog/expected-path counts, original file counts, exercises covered, expected paths matched, additional/unmapped files, missing/not-attempted paths and decode failures. Include `complete: false` unless the defined accessible scope is fully verified; describe that scope. Never equate 619 mapped exercises with all 1,345 exercises having videos.
- **`missing.jsonl`:** one row per unresolved exercise or expected filename, with status such as `no_static_mapping`, `not_attempted`, `not_available_in_app`, `access_denied`, `incomplete_download` or `validation_failed`. Include only observed reasons; lack of a static mapping is not a download failure.
- **`failures.jsonl`:** exact failed operation, exercise/filename, sanitized error and retry status. An empty file is fine if no failures occurred.
- **`SHA256SUMS`:** hashes of media and metadata using relative paths; exclude the checksum file itself.
- **`PROVENANCE.txt`:** macOS/architecture, app version/bundle identity, acquisition date/method, source locations in sanitized form, tools and validation limits. State explicitly whether any file is a recording; recordings are not accepted in this export.

Keep video filenames compatible with [the existing importer](../../../tooling/import_reference_apk.py): either a flat video directory or the `videos/` subdirectory works. Unknown originals should remain preserved and unmapped rather than forced into a known filename.

## Evidence boundary

The handoff input is metadata, not a media export or a license for the source content. It contains no instructions, source images/videos, device identifiers, account records or token values. `input-summary.json` records the reference artifact hash and map digest. Acquisition notes from the Linux investigation remain private; the findings above preserve their limits.
