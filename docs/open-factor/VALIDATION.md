# Validation record

Development checkpoint, 21 October 2026. This record validates the current training foundation, not full reference-app parity.

## Build and static checks

- Debug Android APK builds with JDK 17, Gradle 8.11.1, AGP 8.9.2 and Android SDK 35.
- The earlier nine-test foundation suite covered: completed working-set volume, kg/lb conversion and input bounds, full backup round-trip, rejection of unsupported backup versions, rest deadline/dashboard preference persistence, primary/secondary muscle set contributions, gym profile/weight backup compatibility, equipment alternative-group requirements, and direct equipment references versus group references.
- Android lint passes without errors. Remaining warnings concern dependency versions, convenience API suggestions and backup configuration.
- Local APK import yields 1,345 unique exercise IDs, 619 exact image joins and 1,213 instruction joins. It handles nullable bodyweight values, preserves the source catalog, and does not fabricate video URLs.

## Device checks

Connected phone: CPH2745, Android 17. Two instrumented tests pass: persistent workout data through a new storage instance, and opening every referenced local exercise image/instruction asset. Initial Compose/Espresso-driven tests failed before application assertions due to test-framework compatibility; the retained instrumentation tests use AndroidJUnit4 for device storage/media, and the UI flow uses explicit ADB actions.

Disposable read-only Pixel 7 emulator: Android 15, 1080×2400, density 420. `tooling/android_smoke.py` verifies starting a session, searching/adding an exercise, completing a set, force-stopping/restarting the application, resuming the saved session with its rest countdown, finishing it and reading the persisted history. It also captures the dashboard, logger, completion summary, exercise list and settings as native screenshots. The emulator's workouts are generated test data.

The gym UI was also checked on the emulator: create a blank profile, select Barbell, edit its weights to synthetic values, restart the process and verify the profile, equipment ID and weight list in persistent storage.

Raw screenshots and execution logs remain local. The UI smoke runner deliberately requires an `emulator-*` serial and does not clear application data. It leaves its generated test workout in that disposable instance.

## Reproduce

```sh
cd openfactor
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
cd ..
python tooling/android_smoke.py --serial emulator-5554 --output /tmp/open-factor-review
```

## Unverified requirements

Reference-app streamed videos, source program algorithms and complete visual/interaction parity remain unverified. See the investigation matrix for the outstanding features. No complete-clone acceptance has been recorded.

## Full public dataset and optional media checks

The source-only clean build was checked with the entire ignored reference directory absent. Its APK contains zero `assets/reference/` entries. Both instrumented tests pass against this build: 1,345 unique exercise records, no original instruction/media payloads in the descriptive catalog, 297 equipment entities, 89 literal commercial preset IDs, and bench-press equipment requirements. Public browsing was captured natively. The reference directory was restored after the build.

All 1,345 browse-category joins and all 1,831 video path/selection-order joins match their independent static-recovery results. Live catalog count discrepancies remain documented in the investigation ledger.

Optional video ingestion and native playback were tested using an explicitly synthetic two-second test-pattern MP4, joined to an exact filename suffix in a temporary build. Import matched one clip and retained absent values for the other angles; the native player rendered the moving test pattern. Backgrounding the app paused playback and returning showed the play control. The temporary test assets were removed from source. No original streamed video was used or verified in this test.


## Current logger checkpoint: emulator-only

Commit `13e860ac` adds persisted Exercise Auto-Next and fixes saved duration to exclude pauses. Thirteen unit tests pass. Added cases cover pause/selection backup, long paused intervals, finishing while paused, history reload, atomic completion/rest/auto-next, disabled auto-next, partial completion, all-complete selection, unchecking and older preference backups.

On paku Android 8/API 26 (`emulator-5554`, 1080×1920), the full native smoke passed with active session, pause/resume and rest state surviving process restart, completed sets saved to history, and six refreshed native captures. A separate two-exercise UI check switched selection, restarted the process and verified restoration before discarding its temporary workout. A synthetic own-app state check then completed the last set on the second exercise, verified selection wrapped to unfinished work and rest started, finished a workout while paused, and reopened its summary after restarting: two active minutes plus five paused minutes displayed **2 min**. No source app, phone, or account was accessed by these tests.

Local source-reference media remain separate from the public APK. This checkpoint still does not verify source completed-summary equivalence, actual TalkBack speech, every metric/set type or the full clone. The observed two-exercise auto-next behavior is supported; more complex ordering remains a reconstruction policy.


A follow-up native error-recovery check entered zero reps, captured the visible `Enter 1–9999` error, verified attempting completion left the stored set incomplete and retained its last valid reps, then corrected the field to 12 and successfully completed/persisted it. The temporary workout was discarded. This covers that visible reps recovery path only; weight/RIR recovery and actual TalkBack announcements remain unverified. It was run after `logger-04.md` and does not retroactively change that independent verdict. The smoke runner now retries transient Android UI-tree-not-ready results and targets the navigation Workout entry explicitly when Settings contains a row with the same name.

The current public APK was rebuilt with the ignored reference pack temporarily absent, then the pack was restored. Its ZIP contains zero `assets/reference/` entries. Build, lint, unit tests and Android-test APK compilation pass for that public build. No new connected instrumentation run is claimed for this checkpoint.
