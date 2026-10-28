# Open Factor

## Current direction — 29 October 2026

The user's clarification makes recovered exercise media a core capability: “simple” means an uncluttered interface with meaningful training features. Original videos/photos, instructions, human anatomy and equipment artwork differentiate Open Factor. Discovery opens rich guidance first; the logger exposes a photo and Guide action in a sheet above the active workout, preserving the current set. Anatomy supports muscle selection and original male/female figures. The active program remains visible on Workout.

The 0.3.0 implementation keeps the charcoal/white system, bundled public DM Sans, Nuvio navigation and grouped settings. One unit choice completes first run. Start on Workout, select an exercise, enter weight and reps, add warmups, complete sets and finish into history. Titles have no explanatory captions. Exact MacroFactor reproduction is no longer the acceptance target; advanced training and body-data features remain available through clear actions.

Core training works offline without an account. Original photos, anatomy, equipment and available instructions belong in an efficient static pack; original video uses explicit Play over HTTPS and a 128 MiB least-recently-used cache. Opening guidance does not start streaming. Cached bytes may be evicted, so the cache is not a permanent offline library. Full local-video builds remain optional. The Hono/Node/RustFS service implementation and Dokploy configuration live in `backend/media/`; configuration alone is not evidence of deployment or uploaded-object availability. Accounts are not required or implemented by that media service.

The recovered collection has 1,831 videos for 619 exercises, 1,832 photos, 44 muscle SVGs, four body assets and 297 equipment images. The current staged static manifest records 32,986,897 bytes, with equipment thumbnails derived at up to 256 × 256 pixels and no MP4 payload. Not every catalog exercise has original guidance. See [media packaging](docs/open-factor/MEDIA-PACKAGING.md) and [asset provenance](docs/open-factor/ASSETS.md).

Build mode distinguishes content: `auto` includes the original static pack when present and otherwise falls back to `source`; `static` requires the pack, `source` excludes it, and `full` also includes local videos. Source-only builds retain public DM Sans, the bounded Everkinetic illustration pack and descriptive metadata. The earlier 0.2.0 source-only release remains a historical artifact, not the complete intended media experience. Recovered assets stay outside Git and retain their original ownership independently of the GPL code.

Active sessions save durably after changes and resume after process recreation. Optional workout/rest notifications open the active session. Precise background timers require Android special access; force-stop suppresses alarms until reopening. Verification must distinguish source-only and original-media artifacts, static provenance, playback/cache behavior and guidance-to-logger continuity. Final 0.3.0 release, production-service and device-coverage claims require their own evidence.

The following sections retain the historical source-parity brief and investigation context. They do not reinstate exact parity, offline-only networking restrictions or source-only content as current product requirements.

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

An independently maintained GPLv3 project based on NuvioMedia/NuvioMobile, with base-app credits and original license notices retained. The Android workout application uses Kotlin and Jetpack Compose, matching the base project's technology. The upstream media application is retained as reference; Open Factor builds separately to avoid requiring streaming services, media engines, or their credentials.

## Product Purpose

Build an open source workout application called Open Factor. Preserve the features, exercises, interactions and screen layouts observed in the supplied MacroFactor Workouts APK, with Nuvio's floating pill navigation and settings presentation as the user's explicit exceptions.

## Users

People recording and reviewing workouts on Android. Further audience details are undecided.

## Capabilities and Constraints

The requested scope includes exercise discovery and details, equipment, workout/program creation, set logging, workout history, progress, settings, and working exercise media. Feature completeness must be measured against APK and runtime evidence rather than assumed from names. GitHub progress commits are explicitly authorized.

The application is offline-only: no required account, subscription, Internet permission or remote media. Preserve the floating navigation pill with a working plus action in its middle, between Workout and Exercises. Exercise videos fill their demonstration area without a seek bar, with 1X, 2.5X and 5X playback options as explicitly requested. Local body data, photos, analytics and optional device-local integrations remain parity requirements rather than being dismissed as cloud features. See `docs/open-factor/PARITY.md` for the acceptance gaps.

The code is GPLv3, inherited from Nuvio. Asset provenance and redistribution rights must be recorded independently of the code license. Local reference imports must not silently be represented as GPL assets.

## Brand Commitments

Name: Open Factor. Preserve the workout app's observed UI and UX, except use the Nuvio navigation pill and Nuvio-style grouped settings. The supplied screenshots are the visual authority for those two surfaces.

## Evidence on Hand

- `/home/buhh/Downloads/Workouts.apk` (MacroFactor Workouts, installed package `com.sbs.train`, version 1.4.1).
- Connected Android phone; all four APK splits copied locally through REA.
- Nuvio upstream commit `c78756d0cbda3337cfff36a95d8b6708f48c0d72`.
- Two user screenshots of Nuvio navigation/settings.
- Private inspection evidence outside the public repository. Feature and asset inventories belong in `docs/open-factor/`.

## Product Principles

1. Match observed behavior and record unresolved gaps honestly.
2. Keep exercise and workout data useful offline.
3. Preserve user data across navigation and app restarts.
4. Keep a reproducible public build and attributable asset pipeline.

## Open Decisions

- Exact source generation, progression, deload and populated analytics behavior remains incompletely recovered. Do not invent policies and label them source-equivalent.
- Original media is verified and imported locally; distribution rights, package size and broad physical-device codec compatibility remain separate decisions.
