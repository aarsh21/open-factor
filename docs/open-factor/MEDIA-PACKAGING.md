# Exercise media packaging

The 0.5.1 direction keeps original guidance central while making logging straightforward. Static photos, instructions, anatomy and equipment previews are packaged in the app; original videos are separate, explicitly played content. Source-only builds remain a distinct fallback. This document describes the implementation and current staging inventory, not a deployed-service or final-release certification.

Local Android builds use the extracted exercise photos, anatomy, instructions and equipment illustrations when the ignored reference pack is present. They keep the original exercise photos and anatomical SVGs intact. Equipment previews are derived at a maximum of 256 × 256 pixels under the same asset paths. Imported originals are never changed.

`openfactorMediaMode` selects the build:

| Mode | Static reference assets | Original videos |
| --- | --- | --- |
| `auto` (default) | Included when `reference/catalog.json` exists; otherwise source-only | Separate delivery/cache |
| `static` | Required and included | Separate delivery/cache |
| `source` | Excluded | Excluded |
| `full` | Required and included | All locally imported MP4s included |

Run commands from `openfactor/`:

```sh
# Everyday local build: original static media, without a multi-gigabyte video APK.
./gradlew :app:assembleDebug

# Reproducible source-only CI/distribution build, including on a machine with extracted media.
./gradlew :app:assembleDebug -PopenfactorMediaMode=source

# Explicit full offline-video build; the imported video payload alone is about 2.73 GB.
./gradlew :app:assembleDebug -PopenfactorMediaMode=full
```

The legacy `-PincludePrivateReference=false` remains an explicit alias for `source`; `true` opts into `full`. Conflicting explicit source exclusion and reference modes fail. `-PopenfactorReferenceDir=/absolute/path` selects a different imported reference pack. Extra body-reference assets through `openfactorBodyReferenceDir` are available only outside source mode.

Static/full staging needs Python 3.11+ and ImageMagick's `magick` command. Source mode needs only Python. Staging writes `app/build/generated/referenceMediaPack/`; Gradle synchronizes it with the regular app assets into `app/build/generated/bundledAssets/`. Changing modes removes stale assets, including videos. Static reference data has a 64 MiB uncompressed budget, excluding videos. Unused raw source dumps, app icons, Rive animations and unrelated font families are omitted.

The generated `media-pack.json` records the mode, source APK/catalog hashes, file counts and exact source/output SHA-256 values. Equipment thumbnail transformations are identified individually. The reference catalog retains original clip identities and `videoObjectPaths`. The implemented Android media client resolves those identities to a bundled clip or the configured HTTPS service; an accessible server and uploaded object are still required for uncached remote playback.

## Reusing the published static pack in release CI

Release CI does not need the ignored original directory or an ImageMagick installation. It downloads the reviewed `v0.8.0` APK into a fresh temporary directory, verifies its pinned SHA-256 and signing certificate, then passes `openfactorMediaPackApk` and `openfactorMediaPackSha256` to the static build. `tooling/reuse_release_media.py` invokes the full media/backend verifier in isolated Python before copying only manifest-listed static assets into the generated pack. No APK code, native libraries or unrelated assets are extracted or executed. Paths, lengths, digests, output ownership and bounds are checked again before staging.

The entire existing `media-pack.json` and all 3,394 asset bytes remain unchanged, including original-source digests and equipment-thumbnail transformation receipts. This is reuse of the already published pack, not regeneration from reduced equipment thumbnails or a new provenance claim. The finished APK is verified separately. The baseline pin and certificate live in the reviewed release workflow; replacing media requires an explicit baseline/verifier review rather than silently following the latest uploaded asset. Normal local static/full staging and explicit source exclusion remain unchanged. See [release operations](RELEASING.md).

## Current original-media inventory

The inspected collection contains 1,831 MP4s for 619 exercises, 1,832 exercise photos, 44 muscle-group SVGs, four body assets and 297 equipment images. There are 1,213 instruction files, of which 1,212 contain text. These counts do not establish full media or instruction coverage for all 1,345 catalog exercises. The current static staging manifest records **32,986,897 bytes** before APK compression and **zero bundled MP4s**. The roughly 2.73 GB video payload remains outside static mode.

## Playback and cache

`ExerciseMedia` shows the photo preview while the visible Guide prepares automatic muted playback. One ExoPlayer playlist preserves the original numbered camera order and loops all available angles. Only the currently visible, resumed guide can prepare a player or open its video source. Leaving Guide, closing it or backgrounding the app stops loading and releases the player; returning to a guide resumes automatic playback unless the user explicitly paused it. A manual pause survives background/foreground and recreation, and failures require explicit Retry or camera selection rather than an automatic retry loop. Direct camera selection remains available. The portrait fills the available width at its natural aspect, with camera previews overlaid on the side. It has no added black side margins and preserves the full movement frame. On shorter screens the image scrolls rather than shrinking into a narrow centered strip. A compact floating speed/transport capsule overlays the top of the picture, with reserved space for side camera previews. Error/Retry remains directly above the picture. Controls expose 1X, 2.5X and 5X speed, pause and replay without a seek bar. A bundled MP4 takes precedence; otherwise the original `videos/<exercise-id>_<view>.mp4` identity resolves under `exerciseMediaBaseUrl`.

The build currently defaults that base to `https://factor-media.zabimaru.me/v1/`; override it with `-PexerciseMediaBaseUrl=https://your-host/`. This is build configuration, not evidence that the hostname or indexed objects are available. The client accepts HTTPS bases without URL credentials, query parameters, fragments or traversal; cleartext traffic is disabled. [The media service](../../backend/media/README.md) uses Hono/Node in front of private RustFS storage and includes Dokploy configuration and authenticated upload tooling.

Remote video uses a **128 MiB disk cache** with least-recently-used eviction. It caches bytes requested during playback, does not prefetch the video library, and can stream around a cache error. Cached bytes can be removed by eviction or Android cache clearing; only retained complete data can support an offline replay. Static guidance and workout logging remain usable without the video service. Missing installation/configuration and playback failures retain a preview and explicit status or Retry action. The player pauses with the activity and releases pending playback/network work when its surface stops or leaves composition.

The active logger's horizontal tabs switch exercises with one tap and keep the selected tab visible; a photo/title header identifies the current exercise. Its separate Guide action opens Guide, Muscles and History in a sheet above the workout. Closing it returns to the current set. The library opens this rich detail before its secondary settings action. The Muscles tab supports original male/female artwork, readable full-width figures and selectable primary/secondary muscles. Source-only builds use their licensed preview/icon fallback rather than claiming the original pack is installed.

## Ownership and distribution

The imported reference directory and build output remain ignored. No imported photos, videos, instruction text, anatomy or fonts are committed by this workflow. Their ownership remains separate from the application's MIT license. Source-only public artifact checks continue to reject `assets/reference/` entries; reference-containing distributions need an explicitly appropriate distribution policy and verifier.
