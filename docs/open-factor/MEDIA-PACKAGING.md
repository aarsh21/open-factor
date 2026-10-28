# Exercise media packaging

The 0.3.0 direction keeps original guidance central while making logging straightforward. Static photos, instructions, anatomy and equipment previews are packaged in the app; original videos are separate, explicitly played content. Source-only builds remain a distinct fallback. This document describes the implementation and current staging inventory, not a deployed-service or final-release certification.

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

## Current original-media inventory

The inspected collection contains 1,831 MP4s for 619 exercises, 1,832 exercise photos, 44 muscle-group SVGs, four body assets and 297 equipment images. There are 1,213 instruction files, of which 1,212 contain text. These counts do not establish full media or instruction coverage for all 1,345 catalog exercises. The current static staging manifest records **32,986,897 bytes** before APK compression and **zero bundled MP4s**. The roughly 2.73 GB video payload remains outside static mode.

## Playback and cache

`ExerciseMedia` shows the photo preview first. Opening Guide performs no video download. Play creates the player and opens its data source. The client preserves original numbered camera views and exposes 1X, 2.5X and 5X speed, pause and replay without a seek bar. A bundled MP4 takes precedence; otherwise the original `videos/<exercise-id>_<view>.mp4` identity resolves under `exerciseMediaBaseUrl`.

The build currently defaults that base to `https://factor-media.zabimaru.me/v1/`; override it with `-PexerciseMediaBaseUrl=https://your-host/`. This is build configuration, not evidence that the hostname or indexed objects are available. The client accepts HTTPS bases without URL credentials, query parameters, fragments or traversal; cleartext traffic is disabled. [The media service](../../backend/media/README.md) uses Hono/Node in front of private RustFS storage and includes Dokploy configuration and authenticated upload tooling.

Remote video uses a **128 MiB disk cache** with least-recently-used eviction. It caches bytes requested during playback, does not prefetch the video library, and can stream around a cache error. Cached bytes can be removed by eviction or Android cache clearing; only retained complete data can support an offline replay. Static guidance and workout logging remain usable without the video service. Missing installation/configuration and playback failures retain a preview and explicit status or Retry action. The player pauses with the activity and releases pending playback/network work when its surface stops or leaves composition.

The active logger's photo/Guide action opens Guide, Muscles and History in a sheet above the workout. Closing it returns to the current set. The library opens this rich detail before its secondary settings action. The Muscles tab supports original male/female artwork, readable full-width figures and selectable primary/secondary muscles. Source-only builds use their licensed preview/icon fallback rather than claiming the original pack is installed.

## Ownership and distribution

The imported reference directory and build output remain ignored. No imported photos, videos, instruction text, anatomy or fonts are committed by this workflow. Their ownership remains separate from the application's GPL license. Source-only public artifact checks continue to reject `assets/reference/` entries; reference-containing distributions need an explicitly appropriate distribution policy and verifier.
