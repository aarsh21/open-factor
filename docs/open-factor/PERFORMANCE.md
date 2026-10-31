# Performance and synthetic training data

## Logging and scrolling pass, 2026-10-31

This pass targeted work a person feels while logging sets and scrolling tabs. Every number below is a host-JVM median from `PerformanceEvidenceTest` (run with `:app:testDebugUnitTest`; lines start with `PERF`). In each case the "before" figure comes from a replica of the replaced code in the same run. These are relative CPU costs, not Android frame times. The heavy data is 2,000 workouts, 2,000 weight readings, the real 1,345-exercise catalog and an 8-exercise × 5-set active workout.

| Problem (code-verified) | Before → after | Fix |
| --- | --- | --- |
| Every typed digit encoded the whole live snapshot and committed it synchronously on the main thread. The snapshot holds all weight readings, programs, gyms and so on, and the encoding was then parsed back up to three times. | 50 rapid edits: 50 main-thread commits → **1 background write**, 0 on the caller. Snapshot CPU per save: 7.2–9.1 ms → **1.6 ms** (114 KB). | `CoalescedSaver` and `WorkoutHistoryArchive` identity caches (see Storage safety, item 6). |
| The Exercises tab recomputed `performedIds` (a flatMap over all history) and two more sets on every shell recomposition. Scrolling any tab recomposed the shell on every scroll frame: `scrollEnergy` was read in composition by `LaunchedEffect(scrollEnergy, tab)` and passed by value to the bar. | Per scroll frame: 0.78 ms of set building → a remembered key check (**0.07 µs**). The shell no longer recomposes while scrolling. | `remember(data.history)` / `remember(data.exerciseDefaults)`. `scrollEnergy` is observed with `snapshotFlow`; the bar drift is an `Animatable` read only in `graphicsLayer`; the pill offset is read in the layout phase. |
| The 1-second `now` lived at the top of `WorkoutSession`, so every tick recomposed the session body and re-ran the effort-prompt scan. | Session-body recompositions per tick: 1 → **0** (once when rest ends). The effort-prompt scan: 27.8 µs → **1.6 µs** per recomposition. | `SessionClock.kt`: leaf `SessionElapsedText`/`RestCountdownText` plus a `derivedStateOf` resting flag. A first-match catalog map replaces the linear finds. |
| Each keystroke recomposed every `SetRow` of the exercise. Strong skipping compares captured unstable values by identity, and the callbacks captured the per-edit `SessionExercise`/`Workout` copies. | Rows recomposed per keystroke: all rows → **only the edited row** (device test below). | The callbacks capture ids. |
| The app shell (`OpenFactorApp` top scope and the window root) read the whole `AppData` and recomposed on each digit. | Shell recompositions per keystroke: 1 → **0** (device test below). | The shell reads `derivedStateOf` slices. Routes and dialogs read `data` in their own scopes. |
| `Exercise.title` compiled its regex on every call, in list rows and set rows. | 1.64 → **0.71 µs** per title. | Hoisted `Regex`. |
| DM Sans `Typeface` was parsed from the 240 KB asset 3× at theme start, plus once per `PageHeading` (34 call sites) on each screen entry. Each was a new family instance, which missed Compose's typeface cache. | Up to one parse per weight per process (4 total). | `dmSans()` process cache. |

`RecompositionBudgetTest` (instrumented; uses debug-only `RecompositionTrace` counters that are compiled out of release by `BuildConfig.DEBUG`) asserts the following:

- With a frozen Compose clock, four timer ticks recompose the clock but not `WorkoutSession`, any `SetRow` or the shell.
- Typing in one row recomposes only that row, never the shell, and the value reaches storage within the coalescing window.
- A typed value is in storage as soon as the activity pauses, and completion is immediately durable.
- Scrolling the Exercises tab does not recompose the shell. This check is skipped when system animations are off, because scroll energy then never changes.

These tests were compiled but not run in this pass. Run them on an emulator you own.

**Checked and left alone:**

- `ExerciseImage`: Coil 3 `AsyncImage` without an explicit size already decodes at the laid-out constraints (42–46 dp previews of 360×640 WebP frames).
- Release already uses R8 and resource shrinking. `androidx.profileinstaller` is already a transitive runtime dependency of Compose UI, and the release APK already contains the libraries' `assets/dexopt/baseline.prof`. An app-specific baseline profile needs a device-generated profile, so none was hand-written.
- The library search still runs in composition, at 1.8 ms median / 2.8 ms max per keystroke on the JVM. Moving it off-thread would change result timing for existing journeys.
- At startup, `store.load()` decodes the history archive on the main thread: 84 ms on the JVM for 2,000 workouts / 6.1 MB. Making it asynchronous needs a loading state for all data.
- Catalog loading already runs in the background in `Application.onCreate`, gated by `ReportDrawnWhen`.
- Lazy lists already have stable keys.
- `TemplateScreens.kt` and `Programs.kt` still contain linear catalog finds. They are not on a per-frame path, and other work is in flight there.

**Not measured:** physical-device frame timing (p50/p95 jank, Macrobenchmark), real fsync cost per commit, cold/warm startup time and release-build behavior are all out of scope for a device-free pass. Profile the keystroke and scroll paths on a mid-range phone with system tracing before claiming frame-time improvements.

Compose compiler stability reports can be generated with `-PopenfactorComposeReports=true` (written to `app/build/compose-reports`). They confirm that strong skipping is active: `SetRow` is `restartable skippable`, `LoggedSet` is stable, and `Preferences`/`List<SetMetric>` are compared by instance. This is why capturing ids rather than objects matters.

## 0.7.0 release hardening

The focused working-tree checks below were not the full CI matrix. The later full source CI exposed a large-backup OOM at a 192 MiB heap and a compact-screen import-entry failure. Release hardening streams workout JSON during export, decodes already-parsed backup metadata directly, and reserves the larger keyboard/navigation obstruction rather than both. The entry test verifies its real tap clears navigation, and a 320×560dp/1.4×-type layout test checks both actions and final-target scrolling. These fixes retain the dataset and safety assertions rather than enlarging the heap or skipping the checks. Final artifact and verification scope are recorded in [0.7.0 release notes](releases/v0.7.0.md).

## Changes in the initial working-tree pass

- Dashboard customization caches decoded draft state, option labels and metadata. Body-metric choices no longer construct chart series or traverse saved readings. Exercise search runs on a background dispatcher and reuses the library search index.
- Customization uses compact section cards, one Edit action per section, checkbox selection, and contextual reorder/remove menus instead of two arrow controls beside every row. Save applies the draft; Back discards it; the draft survives recreation.
- The Workout home weekly card emphasizes recorded working sets, labels its calendar range, and links the whole card to History. It has no invented progress percentage or decorative chart.
- Weekly/monthly summaries group workouts by date once. Exercise tiles share one count index instead of rescanning all recorded sets for each tile. History cards reuse the existing catalog map. Weight means use a sliding seven-calendar-day window instead of rescanning all readings for every date.
- Completed history lives outside the small synchronous live-workout snapshot. Unchanged history is not serialized or rewritten for every typed digit. Finishing a workout, including an optional template, is one durable update performed off the UI thread, with a visible saving state and retry error.
- The bounded backup metadata budget includes object overhead, not just file bytes. Its old 10,000,000-unit ceiling rejected the 2,000-workout fixture; a 64 MiB budget now supports this round trip while retaining node, depth, string/token and total-file checks. Large-backup preparation and export/reimport are device-tested. This does not make arbitrarily large fixtures importable.

These are specific bottleneck fixes, not a claim that every screen is universally fast. First load, the first legacy-history migration, history edits, and large full backup operations still scale with the amount of data. Physical-device frame pacing and release-build startup benchmarks remain separate work.

## Storage safety and compatibility

`WorkoutStore` writes compact live data to its existing private preferences. Nonempty history is stored as a compact JSON array in `files/workout-history/<preferences-name-sha256>/<history-sha256>.json`. The small internal snapshot references it with `storageHistory`.

1. A changed history is written with Android `AtomicFile` before committing the referencing live snapshot. Unchanged immutable history lists reuse their archive.
2. Loads verify the archive checksum. Missing or damaged archives do not silently turn into empty history: the existing startup-checkpoint recovery path is used.
3. Current and last-good checkpoint archives remain available. Obsolete unreferenced archives are pruned after a successful live commit; pruning is suppressed while preserved recovery payloads exist.
4. Recovery ZIPs retain exact original snapshot strings plus archived history bytes, including damaged bytes, for investigation. A recovery ZIP is not a normal restorable backup.
5. Old self-contained inline storage remains readable and migrates on the next save. Public exports/backups still include all history and contain no private archive references. An internal preferences snapshot alone is deliberately rejected as a restore backup.
6. Typed set values are the only deferred writes. An edit that changes only a set's weight, reps, assistance, duration or distance publishes immediately and is written on a worker at most `TYPED_SAVE_DELAY_MS` (250 ms) after the first digit of a burst. Each write stores the newest accepted state, so an older queued value can never overwrite a newer save. Everything else is committed before the UI publishes it, exactly as before: completion, set kind, RIR, adding or removing sets and exercises, finishing, restore and settings. Pending values are flushed on `MainActivity.onPause` and `WorkoutViewModel.onCleared`. They are also flushed by any synchronous save, such as completing the set. A failed deferred write stays pending and is retried. The only new exposure is that a hard crash or kill without `onPause` can lose at most the last 250 ms of typed digits for a set.

Export a normal backup before moving an installation. Copying only `shared_prefs/open_factor.xml` is no longer a complete private-data copy. For disposable smoke tests, `tooling/storage_snapshot.py` resolves and verifies both inline and archived storage. Tools that replace an installation must preserve **all** its private files or use a disposable app; a raw XML-only restore is insufficient.

## Generate a large test backup

```sh
mkdir -p /tmp/open-factor-performance
python3 -I tooling/generate_workout_fixture.py \
  --output /tmp/open-factor-performance/synthetic-2000-workouts.json \
  --workouts 2000 --exercises 6 --sets 4 --seed 21 --end-date 2026-10-29
```

This creates 2,000 completed workouts, **48,000 working sets plus 12,000 warmups**, 2,000 dated weight readings, and a template. Exercises use real bundled catalog IDs; all training values and notes are explicitly synthetic. Generation is deterministic and refuses to overwrite an existing file. Limits prevent accidentally creating more than one million set rows.

The tool does **not** install, inject, or import anything. Use Settings → Restore Backup in a **disposable test installation** to load the file. Restoration replaces the destination's saved data. Never load it into a personal installation without first exporting and verifying a full backup.

A generated 2,000-workout example was checked locally at `/tmp/open-factor-performance/synthetic-2000-workouts.json` (11,323,980 bytes). Generated personal/test backups stay out of Git.

## Isolated verification package

A signed release may reject a debug APK with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Do not uninstall it to make a test pass. Use a separate package:

```sh
./openfactor/gradlew -p openfactor :app:assembleDebug \
  -PopenfactorMediaMode=source -PopenfactorVerificationBuild=true
```

This opt-in **debug-only** setting builds `org.openfactor.app.verification`, labeled Open Factor Verification, without changing the normal production package or release signing. It has separate data and can coexist with an installed release.

## Automated checks

```sh
python3 -I -m unittest discover -s tooling -p 'test_*.py' -v
./openfactor/gradlew -p openfactor :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebugAndroidTest -PopenfactorMediaMode=source

# Choose an emulator not owned by another automation session.
ANDROID_SERIAL=<disposable-emulator> ./openfactor/gradlew -p openfactor \
  :app:connectedDebugAndroidTest -PopenfactorVerificationBuild=true \
  -PopenfactorMediaMode=source \
  -Pandroid.testInstrumentationRunnerArguments.class=org.openfactor.app.LargeBackupTest,org.openfactor.app.ProgressPhotoStorageTest,org.openfactor.app.SpreadsheetImportJourneyTest,org.openfactor.app.DashboardCustomizationTest,org.openfactor.app.WorkoutHistoryArchiveTest,org.openfactor.app.StorageRecoveryTest,org.openfactor.app.PreferenceStorageTest,org.openfactor.app.TemplateStorageTest,org.openfactor.app.TrainingFlowTest,org.openfactor.app.QuickWorkoutJourneyTest,org.openfactor.app.PastWorkoutEditJourneyTest,org.openfactor.app.RecompositionBudgetTest,org.openfactor.app.AdvancedLoggerStorageTest,org.openfactor.app.WorkoutEffortJourneyTest
```

`LargeTrainingDataTest` covers 5,000 workouts / 120,000 working sets, 20,000 weight readings, duplicate identities, future records, warmups, incomplete sets, compact backup round trips and metadata-only customization. Its timings are JVM diagnostics, not Android frame-time measurements. Device tests use an isolated 1,000-workout / 20,000-set store and verify that logger edits leave the history archive unchanged, as well as migration, pruning, missing/corrupt archives, startup recovery, and self-contained backup semantics. Timing thresholds are deliberately not CI assertions.

## Local verification, 2026-10-29

- Final normal-package source-mode debug and optimized unsigned release builds, Android-test compilation and lint completed. All **213 JVM tests passed with zero skips**, including the explicitly supplied workbook; lint had no errors and existing warnings remain. R8/resource shrinking completed for the release build, but that unsigned artifact was not installed or published.
- Seven Python fixture/resolver tests passed; all 26 Python tooling files parsed successfully.
- API 35: 18 focused customization/storage/training tests passed. Two additional existing UI-automation journeys could not run on that emulator because another session owned its automation connection (`UiAutomationService ... already registered`, then `DEVICE_IN_USE`). That session was left alone.
- The final separate API 26 run passed **28/28 focused tests**, including full first-workout logging/finish/history, saved-workout editing, native spreadsheet parsing/footer clearance, a 2,000-workout backup prepare/export/reimport round trip, bounded-reader rejection, progress-photo regressions, and concurrent store reads during archive pruning. Snapshot reads and pruning are serialized across store instances for the same preferences. The verification package, not the installed release, was used. The synthetic verification installation was disposable and removed before the final test run; no personal/release installation was uninstalled.
- On the API 35 debug emulator, the 1,000-workout test recorded five live saves at about **1.1–1.3 ms** with a **1,817-byte live snapshot**; the unchanged compact history archive was **2,424,031 bytes**. The initial archive save was about **319 ms**. These are sampled synchronous storage-operation durations on one emulator, not p95 frame times, physical-device results, or a measured old-APK/new-APK comparison.
- Native manual inspection covered Workout home and dashboard customization in dark theme on API 26. The supplied Outlift workbook was selected through the real Android document picker, previewed as three programs / 15 days, and cancelled. Matching with the actual catalog reported 44 source names, 33 requiring explicit choices; no full real-catalog Outlift append was claimed. The import footer clearance was corrected and device-tested. A Compose device test also checked the weekly card at 1.4 font scale in light theme.
- The generated JSON backup was then restored through the normal picker/confirmation flow **only into `org.openfactor.app.verification`**. A private-storage read verified all 2,000 workouts, 48,000 working sets, 12,000 warmups and 2,000 weight readings, with history archived. Workout home and a scrolling history were inspected natively. This is an operational large-data UI check, not a frame-time benchmark. Full accessibility certification, tablets, every locale and physical devices are not claimed.

Local XML/log/capture receipts are in ignored `inspection/performance-2026-10-29/`. They are working-tree checks, not a new published release's verification record.

## Repository cleanup scope

Removed 1,664 tracked legacy entries (71,093,391 file bytes, plus four empty gitlink folders): the unrelated upstream Compose/Android/iOS media-player targets, bundled player AARs and ratings assets, Stremio developer docs, root legacy Gradle configuration/wrapper, obsolete iOS/store/release scripts, and eleven legacy build/intake workflows. This includes the upstream auto-close/triage and strict PR-policy automation; old media-player issue forms and release links were replaced with Open Factor Android forms. The unreachable old `WorkoutScreen`/calendar implementation was removed from the active Android source.

Open Factor's Gradle project, media backend, active CI, catalog and licensed assets, release/verification tooling, tests, license, attribution and historical Open Factor evidence remain. Operator-supplied `.amp/` media and private inspection files were **not** purged. This is verified dead-target cleanup, not a claim that every unused symbol or local cache has been exhaustively eliminated.
