# Open Factor

## Current direction — 29 October 2026

“Simple” means an uncluttered interface with meaningful training features. Original videos/photos, instructions, human anatomy and equipment artwork are core capabilities. The library opens rich guidance first. In a workout, horizontal tabs switch exercises in one tap and a photo/title header identifies the current exercise and a separate Guide action opens guidance above the current set. Anatomy shows every primary and secondary target together on original front/back male or female figures. Programs remain accessible from Workout; the home summary shows recorded training rather than an empty active-program card.

The 0.6.1 implementation keeps the charcoal/white system, bundled public DM Sans, floating navigation and grouped settings. First run has three short steps: name and optional gender; optional weight/height, units and direct Health Connect; then weekly workout goal, effort rating and automatic rest/duration. Completing setup requires a name; Skip for now opens Workout and preserves the raw draft and step for later review. Completed installations keep their data and bypass setup. Settings → Profile edits these choices with validation, explicit Save and discard confirmation; Review setup reopens the flow. Weekly goals feed Habits and gender supplies the initial anatomy figure.

Start on Workout, select an exercise, enter weight and reps, add warmups, complete sets and finish into history. Set values are entered on a compact in-app workout keypad with unit-aware steppers, Next and Log set, keeping the focused set visible; Workout settings can restore the system keyboard. Titles have no explanatory captions. Plus opens shortcuts, including a weight-entry sheet that preserves the caller and active workout. All exercise pickers, including template creation, search all 1,345 catalog exercises with ranked names, aliases, abbreviations, combined name/equipment/muscle terms and bounded typo handling. Equipment defaults to All equipment. The catalog and equipment snapshot load once per process on a background worker; search indexes and default ordering are reused with a bounded four-catalog cache, and catalog edits obtain a new index. Fully drawn includes catalog readiness.

Settings groups working controls under You, Training, App, Data and About; unavailable language/energy controls, the ineffective bodyweight-contribution toggle and the former setup ceremony are removed. Exact MacroFactor reproduction is no longer the acceptance target.

Dashboard restores the three Muscles / Sets / Exercises circles using actual completed training. Dashboard offers calendar Week/Month totals and muscle workload without a repeated daily bar chart. Workout retains its compact weekly view. History has a week calendar, dated timeline, original muscle maps and real duration/exercise/set/volume totals. Plus offers Create template for reusable day-specific workouts; programs remain in their separate destination. Log Photos opens Front/Side/Back pose choices first. Selecting a pose opens its native camera; full-resolution output stays private and returns to date/pose/note logging. Workout summaries and exercise history use full-width aligned actual-set tables. Saved workouts have an isolated durable edit draft for name/date/notes, set metrics/effort and exercise/set additions or removals; Save preserves identity and active sessions and guards against stale edits. Log all sets completes valid unfinished logs atomically. One optional reps-in-reserve rating follows completion of an exercise and applies to its last actual working log; skip/rating persists without repeated prompting. By default it is asked when the lifter moves on (another exercise, Finish, or after rest when the logger advances on its own), with "Right after the last set" available in Workout settings. Steps has interactive 7/30-day charts, date inspection, totals, average and best day; missing provider dates remain unknown. Selected-period totals, original front/back anatomy and muscle drilldowns use the same durable workout data, including active completed sets without duplicate workout IDs. Warmups and incomplete sets do not inflate workload. Direct sets count once and secondary-muscle sets contribute half to the displayed weighted total; this is an explicit accounting policy, not a strength or recovery score. Weight charts show a daily reading and trailing seven-calendar-day mean of available readings. Missing days are never zero-filled. See [training data](docs/open-factor/LIVE_DATA.md) and [body charts](docs/open-factor/BODY-CHARTS.md).

Templates offers Your templates and Starter splits, separate from programs. Four editable seven-day starting schedules are available when their catalog exercises exist:

| Starter split | Days 1–7 |
| --- | --- |
| Upper / Lower / Push / Pull / Legs | Upper, Lower, Rest, Push, Pull, Legs, Rest |
| Upper / Lower | Upper A, Lower A, Rest, Upper B, Lower B, Rest, Rest |
| Bro split | Chest, Back, Legs, Shoulders, Arms, Rest, Rest |
| Full body | Full body A, Rest, Full body B, Rest, Full body C, Rest, Rest |

Preview selects one day at a time, showing original target-muscle artwork, exercise photos and prescriptions above a pinned Use split action. Use split saves independent day templates with fresh UUIDs and preserves the seven ordered slots, including real Rest days. Saved workout days expose Edit and a pinned day-specific Start; Rest has no Start. These are editable starting points without athlete attribution or individualized outcome claims.

Create template and Edit template use a full-width composer with target muscles, exercise counts, reorder/remove actions and editable sets, rep ranges where supported, and rest seconds. Add exercises uses the shared ranked catalog picker with multi-selection, retained filters and the same exercise guides: muscle shortcuts, Resistance, Favorites and expanded equipment/gym, type, laterality and muscle filters. Raw text and picker/editor state survive recreation; validation and explicit Save protect saved templates, and leaving changed input asks to discard. Starting a template requires no active workout and creates fresh blank actual logs with separate prescribed rep targets. Existing templates without prescriptions remain readable, and backup/restore carries prescriptions and split schedules; scoped Workouts export includes them as a current library snapshot, not a restorable backup.

Exercise notes have three distinct levels in a workout: pinned (every time), template (only when started from that template) and a muted, dated last-time placeholder that is never copied and can be dismissed for that workout.
Swap exercise (exercise menu in a workout) suggests up to eight same-muscle alternatives available at the workout's gym, never archived or deprioritized ones, with Search all as a fallback; completed sets stay with the original exercise, the template or program day can optionally be updated too, and the snackbar's Undo restores both.

Health Connect uses one Connect/Sync action to request read permissions and immediately import real weight, body fat and daily step aggregates after Android consent. The app persists imported records and sync state and refreshes on every foreground visit after the first connection. Setup offers this same native consent/import action directly. A successful onboarding import saves records and sync state before returning once to the starting-point page; consent alone or an old Connected state never triggers return. Denial, provider/read failure and save failure keep the panel open for feedback and retry. Manual Settings Connect/Sync stays on its panel after success. The latest imported weight may fill an untouched baseline with its original date; typing takes precedence, and saving an imported or unchanged historical baseline does not create a weigh-in for today. It requests no provider write permission. Android 14+ uses the built-in provider; Android 9–13 needs the provider app, and Android 8 keeps the rest of the app available without this integration. See [Health Connect](docs/open-factor/HEALTH-CONNECT.md) for partial-grant, import and provider-test boundaries.

Core training works offline without an account. Original photos, anatomy, equipment and available instructions belong in an efficient static pack. Full-width portrait video has floating 1X/2.5X/5X and transport controls over its top, retains side-mounted angle previews and autoplays muted while the guide is visible and the app is resumed. One ordered playlist loops all available camera angles, with direct angle selection, pause, replay and Retry. Leaving the guide, scrolling the media offscreen or backgrounding releases the player and stops loading; deliberate pause survives a return. Bundled clips take precedence; other clips use HTTPS and a 128 MiB least-recently-used cache. Cached bytes may be evicted; full local-video builds remain optional. The Hono/Node/RustFS service and Dokploy configuration live in `backend/media/`. Accounts are not required or implemented by that media service.

The recovered collection has 1,831 videos for 619 exercises, 1,832 photos, 44 muscle SVGs, four body assets and 297 equipment images. The current staged static manifest records 32,986,897 bytes, with equipment thumbnails derived at up to 256 × 256 pixels and no MP4 payload. Not every catalog exercise has original guidance. See [media packaging](docs/open-factor/MEDIA-PACKAGING.md) and [asset provenance](docs/open-factor/ASSETS.md).

Build mode distinguishes content: `auto` includes the original static pack when present and otherwise falls back to `source`; `static` requires the pack, `source` excludes it, and `full` also includes local videos. Source-only builds retain public DM Sans, the bounded Everkinetic illustration pack and descriptive metadata. The earlier 0.2.0 source-only release remains a historical artifact. Recovered assets stay outside Git and retain their original ownership independently of the MIT code.

Active sessions save durably after changes and resume after process recreation. Optional workout/rest notifications open the active session. Precise background timers require Android special access; force-stop suppresses alarms until reopening. Verification distinguishes source-only and original-media artifacts, static provenance, actual-provider Health Connect reads, playback/cache behavior and logger continuity. The [0.3.0 evidence](docs/open-factor/releases/v0.3.0.md) remains specific to that published artifact; [0.4.0 release evidence](docs/open-factor/releases/v0.4.0.md) remains historical; [0.5.0 release evidence](docs/open-factor/releases/v0.5.0.md) remains specific to that refinement; [0.6.0 release evidence](docs/open-factor/releases/v0.6.0.md) records the profile, Settings, autoplay and catalog changes. The [0.6.1 release evidence](docs/open-factor/releases/v0.6.1.md) records onboarding return and editable starter templates; its finish review is ship for the supplied API35 phone captures at normal and 1.35 font scale. Device checks cover their named configurations, not every Android device.

The following sections retain the historical source-parity brief and investigation context. They do not reinstate exact parity, offline-only networking restrictions or source-only content as current product requirements.

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

An independently maintained, MIT-licensed native Android workout application built with Kotlin and Jetpack Compose.

## Product Purpose

Build an open source workout application called Open Factor. Preserve the features, exercises, interactions and screen layouts observed in the supplied MacroFactor Workouts APK, with its own floating pill navigation and grouped settings presentation as the user's explicit exceptions.

## Users

People recording and reviewing workouts on Android. Further audience details are undecided.

## Capabilities and Constraints

The requested scope includes exercise discovery and details, equipment, workout/program creation, set logging, workout history, progress, settings, and working exercise media. Feature completeness must be measured against APK and runtime evidence rather than assumed from names. GitHub progress commits are explicitly authorized.

The application is offline-only: no required account, subscription, Internet permission or remote media. Preserve the floating navigation pill with a working plus action in its middle, between Workout and Exercises. Exercise videos fill their demonstration area without a seek bar, with 1X, 2.5X and 5X playback options as explicitly requested. Local body data, photos, analytics and optional device-local integrations remain parity requirements rather than being dismissed as cloud features. See `docs/open-factor/PARITY.md` for the acceptance gaps.

The code is MIT-licensed. Asset provenance and redistribution rights must be recorded independently of the code license. Local reference imports must not silently be represented as MIT assets.

## Brand Commitments

Name: Open Factor. Preserve the workout app's observed UI and UX, except use the floating navigation pill and grouped settings described in DESIGN.md.

## Evidence on Hand

- `/home/buhh/Downloads/Workouts.apk` (MacroFactor Workouts, installed package `com.sbs.train`, version 1.4.1).
- Connected Android phone; all four APK splits copied locally through REA.
- Private inspection evidence outside the public repository. Feature and asset inventories belong in `docs/open-factor/`.

## Product Principles

1. Match observed behavior and record unresolved gaps honestly.
2. Keep exercise and workout data useful offline.
3. Preserve user data across navigation and app restarts.
4. Keep a reproducible public build and attributable asset pipeline.

## Open Decisions

- Exact source generation, progression, deload and populated analytics behavior remains incompletely recovered. Do not invent policies and label them source-equivalent.
- Original media is verified and imported locally; distribution rights, package size and broad physical-device codec compatibility remain separate decisions.
