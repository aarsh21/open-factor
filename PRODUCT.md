# Open Factor

## Current direction — 29 October 2026

“Simple” means an uncluttered interface with meaningful training features. Original videos/photos, instructions, human anatomy and equipment artwork are core capabilities. The library opens rich guidance first. In a workout, horizontal tabs switch exercises in one tap and a photo/title header identifies the current exercise and a separate Guide action opens guidance above the current set. Anatomy shows every primary and secondary target together on original front/back male or female figures. Programs remain accessible from Workout; the home summary shows recorded training rather than an empty active-program card.

The 0.5.2 implementation keeps the charcoal/white system, bundled public DM Sans, Nuvio navigation and grouped settings. One unit choice completes first run. Start on Workout, select an exercise, enter weight and reps, add warmups, complete sets and finish into history. Titles have no explanatory captions. Plus opens shortcuts, including a weight-entry sheet that preserves the caller and active workout. All exercise pickers, including template creation, search all 1,345 catalog exercises with ranked names, aliases, abbreviations, combined name/equipment/muscle terms and bounded typo handling. Equipment defaults to All equipment. Exact MacroFactor reproduction is no longer the acceptance target.

Dashboard restores the three Muscles / Sets / Exercises circles using actual completed training. Dashboard offers calendar Week/Month totals and muscle workload without a repeated daily bar chart. Workout retains its compact weekly view. History has a week calendar, dated timeline, original muscle maps and real duration/exercise/set/volume totals. Plus offers Create template for reusable day-specific workouts; programs remain in their separate destination. Log Photos opens Front/Side/Back pose choices first. Selecting a pose opens its native camera; full-resolution output stays private and returns to date/pose/note logging. Workout summaries and exercise history use full-width aligned actual-set tables. Saved workouts have an isolated durable edit draft for name/date/notes, set metrics/effort and exercise/set additions or removals; Save preserves identity and active sessions and guards against stale edits. Log all sets completes valid unfinished logs atomically. One optional reps-in-reserve rating follows completion of an exercise and applies to its last actual working log; skip/rating persists without repeated prompting. Steps has interactive 7/30-day charts, date inspection, totals, average and best day; missing provider dates remain unknown. Selected-period totals, original front/back anatomy and muscle drilldowns use the same durable workout data, including active completed sets without duplicate workout IDs. Warmups and incomplete sets do not inflate workload. Direct sets count once and secondary-muscle sets contribute half to the displayed weighted total; this is an explicit accounting policy, not a strength or recovery score. Weight charts show a daily reading and trailing seven-calendar-day mean of available readings. Missing days are never zero-filled. See [training data](docs/open-factor/LIVE_DATA.md) and [body charts](docs/open-factor/BODY-CHARTS.md).

Health Connect uses one Connect/Sync action to request read permissions and immediately import real weight, body fat and daily step aggregates after Android consent. The app persists imported records and sync state and refreshes on every foreground visit after the first connection. It requests no provider write permission. Android 14+ uses the built-in provider; Android 9–13 needs the provider app, and Android 8 keeps the rest of the app available without this integration. See [Health Connect](docs/open-factor/HEALTH-CONNECT.md) for partial-grant, import and provider-test boundaries.

Core training works offline without an account. Original photos, anatomy, equipment and available instructions belong in an efficient static pack. Full-width portrait video has floating 1X/2.5X/5X and transport controls over its top, retains side-mounted angle previews and uses explicit Play, one ordered playlist looping all available camera angles, direct angle selection, HTTPS and a 128 MiB least-recently-used cache. Opening guidance does not start streaming. Cached bytes may be evicted; full local-video builds remain optional. The Hono/Node/RustFS service and Dokploy configuration live in `backend/media/`. Accounts are not required or implemented by that media service.

The recovered collection has 1,831 videos for 619 exercises, 1,832 photos, 44 muscle SVGs, four body assets and 297 equipment images. The current staged static manifest records 32,986,897 bytes, with equipment thumbnails derived at up to 256 × 256 pixels and no MP4 payload. Not every catalog exercise has original guidance. See [media packaging](docs/open-factor/MEDIA-PACKAGING.md) and [asset provenance](docs/open-factor/ASSETS.md).

Build mode distinguishes content: `auto` includes the original static pack when present and otherwise falls back to `source`; `static` requires the pack, `source` excludes it, and `full` also includes local videos. Source-only builds retain public DM Sans, the bounded Everkinetic illustration pack and descriptive metadata. The earlier 0.2.0 source-only release remains a historical artifact. Recovered assets stay outside Git and retain their original ownership independently of the GPL code.

Active sessions save durably after changes and resume after process recreation. Optional workout/rest notifications open the active session. Precise background timers require Android special access; force-stop suppresses alarms until reopening. Verification distinguishes source-only and original-media artifacts, static provenance, actual-provider Health Connect reads, playback/cache behavior and logger continuity. The [0.3.0 evidence](docs/open-factor/releases/v0.3.0.md) remains specific to that published artifact; [0.4.0 release evidence](docs/open-factor/releases/v0.4.0.md) remains historical; [0.5.0 release evidence](docs/open-factor/releases/v0.5.0.md) records this refinement. Device checks cover their named configurations, not every Android device.

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
