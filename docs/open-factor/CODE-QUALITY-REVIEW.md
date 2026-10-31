# Code-quality review — 2026-10-31

## Scope and method

Repository-wide source review, split into bounded storage/portability, core-training, and UI/integration passes. The lead separately reviews Health Connect, media playback/service, analytics, profile handling, and build/release tooling. One Sol worker runs at a time. Changes prioritize concrete correctness and data-safety defects with regression coverage, rather than large architectural rewrites or formatting churn.

This is a source review and a working-tree patch, **not release acceptance**. Android compilation, lint, JVM tests, instrumentation, visual/accessibility checks, backend TypeScript contracts/typechecking, and deployed-service integration have not passed in this environment. See validation below. Promotional video compositions and private/reference media are outside application-runtime acceptance.

## Storage and portability changes

- Reject malformed present collection/object containers in the shared app-data codec instead of silently treating them as missing and dropping records. Missing legacy fields keep their defaults; optional null objects remain supported. This does not claim every individual scalar in every nested codec is now strict.
- Recover from the surviving startup checkpoint when the primary preferences value is absent, and preserve that checkpoint for recovery export.
- Serialize restore with regular ViewModel updates. Construct photo-record replacements inside the synchronized update, and serialize photo cleanup with restore.
- Verify photo length and SHA-256 during backup export. Reattaching an original atomically replaces an existing damaged hash-named file.
- Reject malformed UTF-8 during streaming backup preparation instead of substituting characters in saved text.
- Reject cached formula values used as spreadsheet rest labels, consistently with the literal-only import policy.
- Avoid serializing and reparsing the scoped-export document. Correct the User Profile export disclosure and documentation to list the saved fields actually exported.

The storage schema, history-archive layout, and public backup format are unchanged. Tests cover malformed fields, legacy defaults, missing-primary recovery, update/restore serialization, photo corruption/repair, malformed UTF-8, formula labels, and scoped profile privacy. These Android/JVM tests are added but unexecuted. Lead inspection corrected a new test's assumption: legacy backups without onboarding decode as already completed, unlike a new installation.

## Core-training changes

- Confirm before converting a saved Myo group to a warmup, since that operation removes component actuals on both sides. Cancel leaves the durable edit draft intact; confirmation keeps the main-set identities and actuals, and history changes only on Save.
- Permit the existing derived deload target range through 13 RIR in the active target editor and its shared validation. Normal sessions still use 0–10. Target validation is independent of whether the source program still exists; recorded effort limits are unchanged.
- Reject fractional, string, overflowing, or otherwise malformed integer program prescriptions and non-Boolean skip flags instead of coercing them. Missing legacy cycle counts/defaults, nullable optional targets, explicit rest zero, and existing deload restrictions remain supported.
- Initialize newly materialized program Failure sets with actual RIR zero, matching live logger and swap behavior. Prescribed RIR remains separate; performed load/reps stay blank, and existing saved logs are not rewritten.

Five JVM regression tests and one Android journey were added for these changes. They have been source-inspected but cannot execute without the Android/Java toolchain. Logging, keypad/swipes, effort timing, history edits, notes/swaps, template composition, program generation/materialization/volume allocation, and their related tests were reviewed in this pass. No broad workout/program rewrite was attempted.

## UI and integration changes

- Treat a retained workout-notification launch intent as consumed on activity recreation, preserving the restored Settings/picker route. Fresh launches and genuine subsequent notification intents still open the active workout.
- Clear removed RIR-field validation when a saved logical set becomes a warmup, including both sides. Confirmation Cancel retains validation; invalid weight/reps still block Save.
- Show an explicit complete-backup privacy confirmation before opening the document picker. The previous disclosure was passed to a Settings-row subtitle that the UI never rendered. The confirmation states that original photos/image metadata are included and the file is not encrypted.
- Derive Home's five recent workouts from recorded start times with stable-ID deduplication, rather than depending on import/storage list order.

Three Android journey tests and one JVM test were added for these changes, but remain unexecuted. Lead integration review also updated the six existing Python device-smoke entry points to choose the file after the new export confirmation; otherwise they would stop at the confirmation instead of reaching Android's Save dialog. Those device-smoke changes received syntax checks, not device execution.

## Build-tooling changes

`tooling/prepare_media_pack.py` now rejects a symlinked generated-output root and symlinks inside the output tree before writing or removing files. Previously, resolving the output root could cause staging cleanup to delete files in a linked directory. A linked manifest could also incorrectly establish ownership of an output directory.

Existing output manifests must also be objects with the supported schema: an empty object or other false-like JSON value can no longer claim ownership and permit cleanup of a directory.

Seven new standard-library tests cover these paths, unmanaged-output preservation, normal generated-media cleanup, and source/output overlap. The three symlink regressions and the invalid-manifest regression were observed failing before their respective fixes and passed afterward. These guards do not claim protection against a separate process racing filesystem changes during staging.

## Source-review observations

The existing implementation already has useful boundaries worth preserving:

- Atomic workout-history archives, explicit recovery exports, staged photo restore, and separate durable drafts.
- Provider-read results merged only after successful Health Connect reads; manual readings retain priority.
- Stable, validated video identities; HTTPS-only client configuration; bounded video caching; lifecycle-owned playback.
- Catalog-bound authenticated backend uploads with checksum/length checks, bounded concurrency, exact byte ranges, conditional requests, and private object storage.
- Training summaries deduplicate workout identities and exclude warmups/incomplete work; missing provider dates are not invented zeroes.
- Source-only Android CI, device-test jobs, and a release gate tied to successful CI on the exact main commit.

These observations are based on code and existing test inspection, not new device or deployed-service evidence. Larger files such as `Data.kt`, `Programs.kt`, and `WorkoutSession.kt` remain future extraction candidates; splitting them without executable Android checks would add unnecessary regression exposure to this patch.

## Remaining concerns

- **Photo checkpoint recovery:** photo deletion/replacement can remove originals referenced by the startup checkpoint. Later checkpoint recovery can recover metadata without all attachments. Addressing this needs an explicit retention/deletion policy; this patch does not silently retain photos someone deleted.
- Provider/SAF errors, disk-full and commit failures, process death, migrations, low-memory/large-backup behavior, and actual device accessibility/performance still need runtime verification.
- The new confirmation UI still needs enlarged-text, TalkBack, and native interaction checks. Notification/process-death behavior requires real Android checks beyond source inspection.

## Combined source-coverage ledger

All three worker passes are finished, and the lead inspected their actual diffs and new regression sources. The combined file inventory was checked against all **97 production Kotlin files** under `openfactor/app/src/main/java/org/openfactor/app/`, with no missing or duplicate primary assignments:

- **Storage/portability — 7:** `Data.kt`, `CoalescedSaver.kt`, `WorkoutHistoryArchive.kt`, `ProgressPhotoStore.kt`, `ScopedDataExport.kt`, `SpreadsheetImport.kt`, `BodyData.kt`.
- **Core training — 21:** logging, keypad/swipes, effort, workout composition/selection, past-workout editing, notes/swaps, templates, programs/generation/materialization/allocation, and `QuickStart.kt`.
- **UI/integration — 27:** whole-file application/navigation, Settings/portability UI, body/photo editors, exercise/gym UI, dashboard customization/shortcuts, home/history/summary/import UI, and small content helpers.
- **Lead supporting areas — 42:** Health Connect; media/playback/cache/library/search/anatomy; analytics/body charts/training metrics; profile/onboarding; palette/theme/motion/shared UI; session/reminder/date helpers.

The final worker flagged eight files whose coverage was not established by its brief. They were reconciled against the earlier actual reads: `QuickStart.kt` was fully reviewed in the core-training pass; `ExerciseHistory.kt`, `ExerciseMetrics.kt`, `SessionClock.kt`, `WorkoutNotifications.kt`, `StepHistory.kt`, `StepsCard.kt`, and `WeeklyWorkoutCard.kt` were read directly by the lead. This is combined source-review coverage, not independent full re-auditing by every reviewer or proof that every execution path is correct.

The lead also read the three production backend TypeScript files, their HTTP contract tests, service/Docker configuration, release/Android/media CI and release scripts, selected media import/staging/verification/uploader tooling, and the test-only Health Connect fixture. Python smoke scripts received targeted integration reads rather than exhaustive behavioral audits. Resource XML parsing is syntax validation only; bundled assets, private media, promotional compositions, all historical documentation, and every individual test file were not exhaustively audited.

## Validation actually performed

- `python3 -I -m unittest discover -s tooling -p 'test_*.py' -v`: 24 tests, 23 passed and 1 optional published-APK test skipped because no baseline APK was supplied.
- `python3 -I -m unittest discover -s backend/media/test -p '*_test.py' -v`: 5 passed; network calls are mocked.
- AST parsing: 34 Python files passed syntax checks. XML parsing: 7 Android XML files passed.
- `bash -n tooling/build_public_release.sh tooling/build_media_release.sh`: passed.
- `git diff --check`: passed at this checkpoint.
- Android Gradle attempt exited before compilation: `JAVA_HOME` is unset and no `java` executable is on PATH. ADB/Android SDK were also unavailable; no JVM or device tests executed.
- Backend Bun test attempt exited before contract execution: `Cannot find package 'hono'`. Backend dependencies are not installed. No install, TypeScript typecheck/build, Docker integration, deployment, or external publication was performed.

Before merging, run the documented Android build/unit/lint and disposable-emulator suites in `CONTRIBUTING.md`, plus the Bun contract/typecheck commands in `backend/media/README.md`, in a provisioned environment. Keep the skipped APK provenance check and actual-provider/device checks separate from the Python test results.
