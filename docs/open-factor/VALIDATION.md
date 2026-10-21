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


## Manual programs checkpoint

Build and lint pass with **21 unit tests** (eight program-specific plus the existing thirteen). Program cases cover Rest-to-workout conversion, seven-cycle defaults, per-cycle target/draft/library backup, launching only the requested cycle, separate planned/actual RIR, skip/rest days, timer overrides including zero, older backups, invalid rep ranges/incomplete cycles, and duplicate program/exercise identities rejected before rendering.

`tooling/android_program_smoke.py` passed on both **emulator-5554 Android 8/API 26 (1080×1920)** and **emulator-5556 Android 15/API 35 (1080×2400)**. Each run created explicitly synthetic own-app data, converted a second Rest day to a workout, configured all seven cycles, edited Cycle 2 and switched to Cycle 3 before saving, renamed/saved/activated the plan, restarted the app, launched Cycle 2 with **8–12 reps / 1 target RIR**, opened the target display, completed a set with its **45-second** rest override, finished and verified program-tagged history, then restarted and found the saved program. Target RIR remained distinct from actual logged RIR. Native stills cover rest/editor/targets/library/session targets/session/summary; all were inspected in the parent pass. The shorter API 26 editor requires scrolling to its target card; the API 35 frame shows it above the footer.

The runner leaves one named synthetic program and workout on each disposable emulator and replaces only its own previous synthetic program entries when rerun. No source app, physical phone, Play Store or account was accessed. Model coverage and these flows do not verify every gym/reorder/delete/draft-conflict branch, TalkBack, larger fonts, every target/error state, or source save/activation equivalence. Source advanced set types, smart generation, progression, calendar placement, import and exact target/apply-scope composition remain open. See `TODO.md` for the separately deferred phone-assisted exercise-video task.


## Program target preservation correction

Commit `a656c638` fixes a review-established data-loss path: reopening differentiated cycle targets, changing only the viewing scope, or saving without edits previously copied Cycle 1 over other cycles. An explicit edit flag now gates target updates; heterogeneous cycles open on Cycle 1, and deliberate edits in the all-cycle view are identified as applying to every cycle.

Build, lint and **23 unit tests** pass. Two added regression cases distinguish unchanged viewing/saving from intentional single-cycle and all-cycle updates. The corrected full native flow passed on Android 15/API 35: Cycle 2 retained **8–12 reps, 1 target RIR, skip enabled and a 90-second rest override** through process restart, target-sheet re-entry, unchanged Save, navigation through the all-cycle view and another unchanged Save. After restoring the skip/rest settings, the plan launched Cycle 2, displayed the planned target, completed with a 45-second rest and retained program-tagged history.

The native runner now verifies that an input is actually empty before replacing it and checks the exact replacement value. Earlier attempts exposed unreliable burst key events and compact-height scrolling in the test harness; those failed attempts are not counted as passes. Android 8/API 26 passed the same correction assertions and launch/completion/history checks after resuming from the saved synthetic plan once the clipped-control test issue was corrected. That API 26 result combines the original create/configure/save prefix and the resumed verification; it is not represented as a single uninterrupted run. Its reopened-target capture also shows the normal numeric keyboard; skip/rest persistence is established by the stored-state assertions, not by that one still.

The corrected source-only public APK contains zero `assets/reference/` entries. Build, lint, 23 unit tests and Android-test APK compilation passed with the ignored reference pack absent; the pack was restored afterward. No new connected instrumentation run is claimed. Public APK SHA-256: `b4115ac869e0cffed9e5241bfb16cf6f6be0722b74ba05993584e353a4855765`.


A post-correction independent design consistency check compared the program code with PRODUCT.md, both surface contracts, DESIGN.md and its sidecar. It reported no new global design-system drift and preserved both incumbent files. Existing font-role leakage, compact touch-target uncertainty and logger fidelity gaps remain open; this read-only check did not establish TalkBack speech or full clone acceptance. The independent second manual-program verdict, [programs-02.md](reviews/programs-02.md), remains **fix**: two original findings resolved, three partial and one unresolved. The P1 cycle-target preservation path and named header hierarchy are resolved. Remaining obligations include exact target controls/icon selection/saving scopes, the source rest pictogram (the current umbrella is approximate), actual accessibility/recovery verification and effective catalog counts. No correction regression was established in the supplied evidence; this is not full program or clone acceptance.
