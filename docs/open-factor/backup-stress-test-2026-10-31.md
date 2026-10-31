# Local backup stress test — 2026-10-31

## Current verdict — fixes verified

**All three reproduced defects are fixed, and the targeted backup/recovery suite is green.** The complete JSON backup preserves the tested original pictures, weight/body records, workout data and rich application state during normal export/restore, including large datasets. The initial failing results below are retained as historical evidence, not current failures.

The original investigation added tests only. In the follow-up fix, production version validation, recovery-aware photo cleanup, and buffered/bulk photo export were changed. No commits or pull requests were created. No cloud-sync feature was added.

## Follow-up fixes and revalidation

- **Recovery-safe cleanup:** retain picture references from the startup checkpoint in addition to current records/drafts. Skip destructive cleanup if a checkpoint is unreadable/unsupported or preserved raw recovery payloads exist. Only photo metadata is decoded for retention; workout archives are not reloaded. Pictures removed from the visible dataset can remain privately on disk while recovery needs them; a later cleanup removes them once the checkpoint advances and no recovery payload requires preservation.
- **Strict schema versions:** accept the integer `1` in the Int/Long representations used by the two JSON readers. Reject fractions, floating-point/exponent values, strings, booleans, and overflowing numbers without lossy coercion and before installing or saving restored data.
- **Efficient photo export:** explicitly forward full-array writes through both FilterOutputStream wrappers and buffer the caller-owned destination. Preserve failure propagation and do not close the caller's output stream.
- Added two JVM tests and four additional device regressions for invalid versions, deletion/checkpoint advancement, damaged/newer checkpoints, and preserved recovery payloads after checkpoint refresh/dismissal. Updated existing photo-pruning checks to verify checkpoint protection followed by eventual cleanup, rather than requiring deletion of recoverable originals.

Verified results after fixes:

- **JVM:** 298 discovered; 297 passed, 1 skipped, 0 failures/errors.
- **Android API 26:** all 50 selected backup/recovery/storage tests passed, none skipped.
- **Android API 35:** all 50 selected backup/recovery/storage tests passed, none skipped.
- Verification app and instrumentation APK compiled successfully. Android lint completed with 0 errors and 27 warnings.
- Both combined 2,000-workout / 60,000-set / 3,650-weight / 3,650-measurement / 48-original round trips passed again, including exact original-byte comparisons.
- Both eight-large-original capacity tests completed export, empty-destination restore and byte verification: API 26 original bytes 134,446,492, backup bytes 179,290,023, complete test body 15,270 ms; API 35 original bytes 134,436,181, backup bytes 179,276,271, complete test body 13,813 ms. Timings include fixture generation/verification and are emulator observations, not device guarantees.
- Focused counting-sink regression: **zero single-byte destination writes on both platforms**; API 26 174 bulk writes / 1,403,239 bytes, API 35 259 bulk writes / 1,403,251 bytes.

Raw post-fix evidence uses run labels `api26-fixed`, `api35-fixed`, and `fixes-build` under the machine-local evidence directory below. The 50-test groups comprise the 24 new tests, the original 21 selected tests, and five `WorkoutHistoryArchiveTest` cases. These are targeted device suites, not the entire application instrumentation suite.

## Execution and safety

- Source baseline: `e72a5d3a`, Open Factor 0.9.1.
- Disposable, headless x86_64 Android API 26 / Android 8.0 and API 35 / Android 15 emulators, 2 GiB emulator RAM, 2 cores, KVM acceleration.
- Source-only verification build: `-PopenfactorMediaMode=source -PopenfactorVerificationBuild=true`; separate application ID `org.openfactor.app.verification`.
- New test fixtures use isolated storage and preference namespaces. All records and images are synthetic. No personal data or physical device was used.
- Two delegated tasks built the round-trip and failure-safety test groups; the parent built the app, ran every reported runtime check, added photo-capacity tests, corrected one test-fixture error, and verified the results.
- Both emulators were shut down after testing.

## Initial pre-fix results

### JVM suite

296 discovered tests: **295 passed, 1 skipped, 0 failures/errors**, across 43 suites. Verification app and instrumentation APK compilation passed.

### Android backup/persistence suite

41 unique checks were exercised per platform, comprising 21 existing tests and 20 new tests. Totals below are deduplicated by class and method, not inflated by repeated runs.

- **API 35: 39 passed, 2 failed.** Both failures are the correctness regressions described below.
- **API 26: 37 passed, 4 failed.** The same two correctness regressions, the large-photo export timeout, and a focused single-byte-output regression that isolates the timeout's inefficient write path. These represent three distinct defects, not four.

Execution detail:

- Final API 35 group: 40 tests, 38 passed / 2 failed; subsequent output-write regression: 1 passed.
- Final API 26 group: 39 tests, 37 passed / 2 failed; subsequent output-write regression: 1 failed.
- The remaining API 26 large-photo test was run separately and failed at the 300,000 ms timeout. It was not repeated in the final group. Its companion attachment-limit test passed both separately and in the final group and is counted once.
- An initial new metadata-budget test incorrectly used a top-level JSON string with Android's strict `JsonReader`. It was corrected to use a valid array-wrapped string and passed on both platforms. That initial harness failure is not counted as an application defect.

## Successful full-data round trips

`BackupRoundTripStressTest` exercises production `ProgressPhotoStore.export`, `prepare`, and `PreparedPhotoBackup.install`, followed by `WorkoutStore.save` and reopening with fresh context/store objects. It deletes source attachment storage before restoring into a genuinely empty destination, so retained source files cannot disguise a missing backup attachment. It also re-exports and restores into a second independent destination.

The combined fixture, which passed on both platforms, contains:

- 2,000 historical workouts, 6 exercises per workout and 5 sets per exercise: **60,000 historical sets**.
- Another **120 serialized sets** across active workout, composition draft, and past-workout-edit snapshots.
- **3,650 weight records** and **3,650 body-measurement records**, containing 21,900 measurement values.
- Dates from 2016-02-28 to 2026-02-24, including leap-day coverage.
- **72 photo records**, 144 photo references, and **48 unique original images** with shared references. Images mix PNG/JPEG; three are real 1024×1024 PNGs individually larger than 1 MiB.
- Rich set values and relationships: warmups, split-side/Myo relationships, assistance, fractional duration/distance, paused workout state, IDs and Unicode notes.
- Programs and program drafts, routines/template splits, custom exercises, gym inventory, exercise defaults, preferences, onboarding drafts, Health Connect provenance and saved state.

Assertions check complete `AppData` structural equality, not just record counts. Every restored original image is checked for byte length, SHA-256 identity, and byte-for-byte equality. Uninstalled preparation and successful restoration both clean their temporary staging directories.

Actual combined-fixture sizes recorded during the final runs:

- API 35 originals: 12,087,481 bytes; complete JSON backup: 29,455,822 bytes.
- API 26 originals: 12,076,254 bytes; complete JSON backup: 29,440,874 bytes. Android image encoders produce slightly different original bytes on the two platforms; each is compared with its own exact source.

The smaller rich fixture also passed, including 12 workouts / 360 historical sets, 32 weights, 32 measurements, and 12 originals used in 24 photo records.

Existing ViewModel-level storage tests passed on both platforms for body data, preferences, advanced workout logging, gyms, photo restoration/sharing/deletion and legacy backups. The largest new fixture intentionally tests storage/serialization directly rather than ViewModel notification side effects.

## Photo-heavy capacity tests

`BackupPhotoCapacityTest` separately exercises eight deterministic 2048×2048 PNG originals, each at least 12 MiB and below the existing 20 MiB per-photo limit. The backup is written directly to a file, not a giant in-memory byte array. Source attachment storage is removed before restore. Restored originals are verified using streaming byte comparisons and checksums.

- **API 35 passed:** 134,436,181 original bytes; 179,276,271 backup bytes. The complete image generation/export/restore/verification test body took 13,243 ms in the final run, with a 201,326,592-byte app heap limit. This is an emulator observation, not a real-device performance guarantee.
- **API 26 timed out:** after 300,000 ms, the test was still inside `ProgressPhotoStore.export`, in `Base64.EncOutputStream.write` / `FilterOutputStream.write`. It had not reached restore. Therefore this large-photo restore case is **not verified on API 26**.
- The generated-stream test for attachments above **20 MiB** correctly rejects the attachment without installing originals on both platforms. It tests the input-size guard, not a valid image exactly at the upper boundary.

## Initially reproduced defects — now fixed

### 1. Recovery checkpoint can reference deleted original pictures

Production locations:

- `openfactor/app/src/main/java/org/openfactor/app/Data.kt:1320` — restore invokes photo cleanup.
- `openfactor/app/src/main/java/org/openfactor/app/Data.kt:893` — cleanup retains current data/draft references, not checkpoint references.
- `openfactor/app/src/main/java/org/openfactor/app/Data.kt:682` — load can recover the older checkpoint.

Reproduction:

1. Save data containing pictures and establish the startup recovery checkpoint.
2. Restore a different, photo-free backup.
3. Simulate a later corrupt primary preference payload.
4. Reopen `WorkoutStore`: its checkpoint restores the prior records, but their original pictures have been deleted.

Observed on API 26 and API 35:

```text
java.lang.IllegalArgumentException: A photo is missing; reattach it before saving
```

Regression: `BackupFailureSafetyTest.startupRecoveryCheckpointRemainsPhotoCompleteAfterAReplacingRestore`.

A normal complete restore passed; this is specifically a restore/cleanup/subsequent-recovery consistency defect. Fix direction: coordinate checkpoint publication and attachment retention so any recoverable metadata snapshot has its complete attachment set.

### 2. Fractional schema version is accepted and can replace existing data with defaults

Production location: `openfactor/app/src/main/java/org/openfactor/app/Data.kt:525` uses `optInt("schemaVersion") == 1`, allowing numeric coercion.

Reproduction: with existing saved records and originals, attempt to restore:

```json
{"schemaVersion":1.5}
```

The restore accepts it instead of rejecting the unsupported fractional version; omitted datasets decode as defaults, so confirming this malformed restore can replace existing data and remove prior pictures. This requires an attempted restore, not merely viewing a file.

Observed on API 26 and API 35:

```text
java.lang.AssertionError: expected java.lang.Exception to be thrown, but nothing was thrown
```

Regression: `BackupFailureSafetyTest.fractionalSchemaVersionCannotSilentlyRestoreAnEmptyDataset`.

Fix direction: validate the schema version's type/value without lossy integer coercion before any installation or persistent-state changes.

### 3. API 26 photo export issues one output call per encoded byte

Production area: `openfactor/app/src/main/java/org/openfactor/app/ProgressPhotoStore.kt:118`, the non-closing `FilterOutputStream` wrapper feeding the Base64 encoder. The actual Settings export passes an unbuffered provider output stream to the exporter at `Settings.kt:64`.

Following the capacity timeout, a focused regression exported one 512×512 original into a counting sink, eliminating disk speed as an explanation for the write-call pattern:

- API 26: **1,401,359 single-byte writes**, 3 bulk writes, 1,403,239 total output bytes. Regression failed.
- API 35: **3 single-byte writes**, 346 bulk writes, 1,403,251 total output bytes. Regression passed.

Regression: `BackupPhotoCapacityTest.photoExportUsesBulkOutputRatherThanOneProviderWritePerEncodedByte`.

The API 26 stack and call-count reproduction identify an inefficient output path, not photo data corruption. Fix direction: explicitly support bulk writes for every relevant output overload and buffer the final destination appropriately; verify on API 26 as well as current Android. No proposed fix was applied or benchmarked during this testing-only task.

## Other failure-safety checks that passed

Both platforms rejected tested missing/corrupt/unreferenced photo assets, invalid Base64/checksums/images, duplicate keys/records, invalid containers, unsupported integer versions, internal storage snapshots, truncated input, unexpected trailing documents, excessive nesting, oversized numeric tokens and strings, and reduced metadata-budget exhaustion.

Injected input/output/flush failures, photo-install failures and metadata-commit failures preserve the tested previous data and original pictures. Failed preparations remove staged files. Missing, empty or damaged local originals abort export instead of reporting a usable backup.

## Reproduction command

Use an explicitly allocated disposable emulator, never an existing personal installation. With JDK 17, Android SDK platform 36/build tools 35.0.0, and a booted emulator selected with `ANDROID_SERIAL`, run from `openfactor/`:

```bash
./gradlew :app:testDebugUnitTest \
  -PopenfactorMediaMode=source -PopenfactorVerificationBuild=true

./gradlew :app:connectedDebugAndroidTest \
  -PopenfactorMediaMode=source \
  -PopenfactorVerificationBuild=true \
  -Pandroid.testInstrumentationRunnerArguments.timeout_msec=300000 \
  -Pandroid.testInstrumentationRunnerArguments.class=org.openfactor.app.BackupRoundTripStressTest,org.openfactor.app.BackupFailureSafetyTest,org.openfactor.app.BackupPhotoCapacityTest \
  --max-workers=2
```

The targeted command now selects 24 new tests and passes after the fixes. The complete post-fix device groups additionally selected existing `LargeBackupTest`, `ProgressPhotoStorageTest`, `BodyDataStorageTest`, `PreferenceStorageTest`, `AdvancedLoggerStorageTest`, `TemplateStorageTest`, `GymStorageTest`, `StorageRecoveryTest`, `WorkoutHistoryArchiveTest`, `BackupExportJourneyTest`, and `ImportOptionsTest`.

Machine-local raw logs, copied JUnit XML/device result files, and the emulator runner are retained under `/home/aarsh/.cache/openfactor-backup-stress.DtsqVD/`. Relevant run labels: `api35-final`, `api26-final`, `api26-photo-capacity`, `api26-output-writes`, `api35-output-writes`. These paths are execution evidence on this machine, not portable repository dependencies.

## Explicit limitations

- Synthetic fixtures, not a test of the user's actual backup or personal photo collection.
- Only API 26 and API 35 x86_64 emulators; not physical devices, every Android version, or every OEM.
- Storage reopening uses new objects/contexts; no real process-kill/reboot or power-loss fault injection.
- Real Storage Access Framework document-provider export/restore completion was not driven end-to-end. The existing UI journey verifies the private-photo disclosure and cancel behavior. Provider failures in the new tests are injected streams; capacity uses local files.
- The full 1 GiB backup limit and 64 MiB metadata budget were not reached. Metadata guards were exercised with reduced budgets; the actual 28 MiB single-string bound was tested with an incremental stream.
- Permissions, credentials, external Health Connect databases and reference-media caches are not claimed as portable user data. Saved Health Connect data/provenance in `AppData` was covered.
- No cloud upload, OAuth, network retry, encryption or multi-device behavior was tested; those features do not exist in this backup foundation.

## Recommendation

The three reproduced defects and targeted regression failures are resolved. Before integrating a cloud provider, still complete a real SAF export/import journey and physical-device coverage. These fixes do not establish cloud-provider policy approval or validate encryption, network retries, or multi-device synchronization.
