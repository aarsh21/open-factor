# Pinned exercise notes and workout history, 22 October 2026

Exercise Note now remains a global pin, separate from the notes saved on a workout entry. This corrects the copy-into-history policy documented in [the earlier exercise-settings unit](exercise-settings-01.md). It does not migrate existing notes or establish full source settings parity.

## Retained source contract

The retained Workouts binary is `S/lib/arm64-v8a/libapp.so`, SHA-256 `870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`. `S` is the private evidence directory defined in [the baseline audit](whole-app-parity-2026-10-22.md).

Recovered metadata identifies function ref122630, entry `0xfa5d3c`, as an `ExerciseSettingsView` form builder returning `List<Widget>`. Its English branch loads these strings into the same note field:

- `Exercise Note`, pool `0x36720`, ref6463.
- `Pinned note about this exercise`, pool `0x36ba0`, ref41464.
- `Pinned to exercise, not saved to your workout history`, pool `0x36bb8`, ref16147, loaded at `0xfa5e08–0xfa5e0c`.
- Field key `note`, pool `0x8188`, ref27195, loaded at `0xfa5e30–0xfa5e34`.

This is a statically bound editor contract, not an isolated string or an observed source Save. Source persistence, exact logger placement, and the timing of pin changes during an existing workout remain unobserved. No source account, phone, or source emulator was operated.

Local evidence is `.amp/in/artifacts/exercise-lifecycle-source/{input-sha256.txt,note-form-metadata.txt,settings-actions.txt,settings-actions-annotated.txt}`. Disassembly uses the installed `llvm-objdump`; annotations reuse the retained metadata readers under `inspection/browse-group-recovery/` and `inspection/video-recovery/model-analysis/`. REA tools were unavailable, so this is ordinary retained-binary inspection, not a new REA Evidence record. Recovered media is unchanged and unpublished.

## Local behavior and compatibility

[ExerciseDefaults.kt](../../../openfactor/app/src/main/java/org/openfactor/app/ExerciseDefaults.kt) stops copying the pin into fresh draft, live, direct-start, and program entries. [WorkoutSession.kt](../../../openfactor/app/src/main/java/org/openfactor/app/WorkoutSession.kt) displays the global pin separately from editable workout-entry notes. [ExerciseSettings.kt](../../../openfactor/app/src/main/java/org/openfactor/app/ExerciseSettings.kt) explains that distinction before Save.

New entries start with a blank workout note. An explicitly entered workout note still survives storage, Finish, history, and backup. Pin edits and removal update the separate live display without rewriting the entry. That update timing and the pin row's icon/label/placement are local presentation policies, not recovered source behavior.

There is no historical migration. Saved draft, active, and history notes remain unchanged because earlier copied pins cannot be distinguished from user-edited notes. Rest overrides retain their existing precedence, including explicit program zero and whole-second validation. Data codecs, schema, body/photo routes, media, parent smoke scripts, and `PARITY.md` are unchanged.

## Verification boundary

Two existing JVM checks failed against the old implementation after their expected fresh notes changed to blank. Both failed with `ComparisonFailure`; `.amp/in/artifacts/parity-implementation-11/before.log` retains the reproduction. Existing native coverage now exercises separate entry notes through Finish and verifies unchanged prior history and body/photo fields.

Final evidence is under `.amp/in/artifacts/parity-implementation-11/`:

- Public and private assembly, lint, all 51 JVM tests, and Android-test assembly pass. Commands use `./openfactor/gradlew -p openfactor :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=2`; public packaging starts with `:app:clean` and temporarily removes only the reference-pack symlink. Build logs and `*-build-validation.json` retain identities/counts. The public APK contains only the three catalog JSON assets.
- Six native tests and the uninterrupted extended offline library UI flow pass separately on each variant, on owned emulator-5584. `public/` and `private/` retain native logs, `ui/result.json`, installed APK hashes, XML, and captures. The flow covers Save/Cancel/validation, unsaved editor recreation, draft/start, independent entry notes, process death/recreation, live pin edit/removal, Finish, reopened history storage, and unchanged prior history. It also retains existing library filter/scope/context checks. These are local outcomes, not executed source outcomes.
- The private native run additionally starts with asymmetric dated scale fat, visual fat, left/right measurements, and an original generated PNG attachment. `private/populated-native-controls.json` confirms unchanged records and photo bytes through the six tests, including the extended pin/history test and full backup restoration. These controls are separate from the library UI fixture, whose body-measurement/photo arrays are empty. This unit does not rerun the body/photo UI suite.
- Default and 1.3× logger notes, changed/removed pin, editor, and finished history were visually inspected in both font variants. No note/control clipping or overlap was found. A separate private 1.3× scroll capture verifies the entire settings explanation, including the footer that continues below the initial viewport. `render-review.json` records captures and enlarged text bounds. Original synthetic preference/file bytes and font/network settings were restored; final crash buffers are empty. The focused footer probe also restores all original files and font scale.

The first private attempt stopped because the test expected `Resume Empty Workout`, while the fixture's actual workout is named `Empty`. Its partial captures, failed log, and successful restoration remain in `private-attempt-1/`. The corrected full runs use `Resume Empty`; no app change was needed. They are separate complete passes, not a resumed failed run. Temporary verification scripts were removed after the checks.

## Parent integration acceptance

The parent independently verified the integrated correction on emulator-5580. Evidence is under `.amp/in/artifacts/pinned-notes-integrated/`, with a self-contained visual review in `review.html`.

- Public and private assembly, lint, Android-test assembly, and all 51 JVM tests pass. Builds use `--no-daemon --max-workers=1`. The captured public build log is an up-to-date rerun after the first log destination failed. JVM XML reports confirm zero failures, errors, or skips for both variants.
- Each variant passes six native tests and a complete offline library flow through pin Save, active pin edit and removal, independent entry notes, Finish, and history. Installed APK hashes and asset lists are retained. The public APK contains only `catalog-provenance.json`, `catalog.json`, and `equipment.json` in its assets.
- The existing fixture retains two scale records, one body-measurement record, one photo record, and the photo's original bytes through native checks. `native-preservation.json` records these controls separately from the UI flow's disposable fixture.
- Twelve final captures cover the separate notes at default and 1.3× text, the editor, an updated pin, a removed pin, and finished history. Visual inspection found no clipping or overlap. XML records the pin text height increasing from 63 to 80 pixels at enlarged text.
- `restoration.json` confirms exact original preferences and file bytes, font scale, and network settings after both runs. Both final crash buffers are empty.

The first parent attempt expected `muscles.json`, which is not a public asset. The corrected asset assertion and both complete runs pass without an app change. This acceptance covers the local pin integration only. It does not establish source Save outcomes or whole-app parity. The parent also updates the exercise-settings row in `PARITY.md` to supersede the earlier copy-into-history policy.

## Remaining acceptance

1. Observe source note Save/reopen, entry-note editing, Finish/history, and a pin change during an active workout before certifying those outcomes.
2. Recover Modified/Archived membership and transitions. Labels or config fields alone do not justify guessed scopes.
3. Recover equipment-weight and left/right-rest overrides, advanced set/rest behavior, and source ranking. Smart generation/progression/deload, populated analytics, and the wider audit backlog remain open.

This unit starts from the parent's reviewed `aee64f01` tree. The merge retained its private-font heading-role fix, app-night configuration, and reviewed heading report. Parent timing evidence still distinguishes the 23 ms focused reproduction from the original legacy-photo failure. Work remains local and unpushed.
