# Performance and synthetic training data

## Changes in this working tree

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
  -Pandroid.testInstrumentationRunnerArguments.class=org.openfactor.app.LargeBackupTest,org.openfactor.app.ProgressPhotoStorageTest,org.openfactor.app.SpreadsheetImportJourneyTest,org.openfactor.app.DashboardCustomizationTest,org.openfactor.app.WorkoutHistoryArchiveTest,org.openfactor.app.StorageRecoveryTest,org.openfactor.app.PreferenceStorageTest,org.openfactor.app.TemplateStorageTest,org.openfactor.app.TrainingFlowTest,org.openfactor.app.QuickWorkoutJourneyTest,org.openfactor.app.PastWorkoutEditJourneyTest
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

Removed 1,664 tracked legacy entries (71,093,391 file bytes, plus four empty gitlink folders): the unrelated Nuvio Compose/Android/iOS media-player targets, bundled player AARs and ratings assets, Stremio developer docs, root legacy Gradle configuration/wrapper, obsolete iOS/store/release scripts, and eleven legacy build/intake workflows. This includes the upstream auto-close/triage and strict PR-policy automation; old media-player issue forms and release links were replaced with Open Factor Android forms. The unreachable old `WorkoutScreen`/calendar implementation was removed from the active Android source.

Open Factor's Gradle project, media backend, active CI, catalog and licensed assets, release/verification tooling, tests, GPL license, Nuvio attribution and historical Open Factor evidence remain. Operator-supplied `.amp/` media and private inspection files were **not** purged. This is verified dead-target cleanup, not a claim that every unused symbol or local cache has been exhaustively eliminated.
