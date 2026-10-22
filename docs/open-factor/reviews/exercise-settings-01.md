# Exercise defaults and Info journey, 22 October 2026

Root-library exercises now open settings, whose Info row opens a closeable detail sheet. Workout/program pickers and newly created custom exercises retain contextual detail/Add. Notes, rest overrides and Do Not Recommend persist independently of catalog records, workouts and body data. This is a bounded implementation, not full exercise-settings parity.

## Source evidence and local policy

`S/feature-tour/indexed/03-exercise-library.mp4` at 116 seconds directly shows Chin-Up's Info, Exercise Note/Add, Do Not Recommend (off), Edit Duplicate, Equipment Weights/No equipment, Rest Timer/Default/Edit and a partially clipped Rest Between Left/Right Sets/Default/Edit. `S` is defined in [the baseline audit](whole-app-parity-2026-10-22.md). The retained direct frame is `.amp/in/artifacts/private-source-library/exercise-settings-116.png`. Earlier guessed samples at 112/120/128 seconds show different destinations and are not settings evidence. The source scope inventory is `N/source/12-library-scopes.xml` from that audit.

Those source editors, Save outcomes, scope membership and generator exclusion were not executed. The following are explicit local contracts, not recovered source algorithms:

- Notes and rest are copied only into fresh draft/live/direct-start/program entries. Starting an existing draft, decoding, or changing defaults never reapplies them to saved entries. Explicit program rest wins, including zero; blank uses the exercise default, then workout settings.
- Notes display in the logger and remain editable for that entry. Exercise defaults remain unchanged by a session-note edit.
- Performed means any completed set in stored history. Deprioritized means the durable Do Not Recommend flag. The UI explicitly says automatic generation is unavailable; it does not promise a functioning recommendation engine. Manual selection remains available.
- Note/rest editors use explicit Save/Cancel; blank removes the override. Rest accepts whole seconds from 0 through 1800, following the existing local target-editor range. Partial note edits survive process recreation without becoming saved defaults.

## Ownership and checks

[ExerciseDefaults.kt](../../../openfactor/app/src/main/java/org/openfactor/app/ExerciseDefaults.kt) owns the fresh-entry/codec contract. [Data.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Data.kt) stores the map and passes it to new entries; [ProgramData.kt](../../../openfactor/app/src/main/java/org/openfactor/app/ProgramData.kt) preserves explicit target precedence. [ExerciseSettings.kt](../../../openfactor/app/src/main/java/org/openfactor/app/ExerciseSettings.kt), [Exercises.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Exercises.kt) and non-body [MainActivity.kt](../../../openfactor/app/src/main/java/org/openfactor/app/MainActivity.kt) routes own the settings/sheet/scopes. [WorkoutSession.kt](../../../openfactor/app/src/main/java/org/openfactor/app/WorkoutSession.kt) displays the copied note; the existing load-column widths are unchanged.

JVM coverage checks legacy missing defaults, backup round-trip, unchanged draft/active/history entries, rejected timer imports and 75-second defaults versus explicit 45/0-second targets. The sixth native test exercises the public view-model draft/promotion/live/direct/program paths, storage recreation, backup and removal, preserving prior history/body/photo fields and restoring the original backup in `finally`.

[android_library_smoke.py](../../../tooling/android_library_smoke.py) extends the existing offline library flow with settings/Info/close/return, Save/Cancel/invalid input, process recreation, large text, contrasting scope membership, contextual picker Add, visible logger notes and clearing defaults without rewriting an active entry. Its synthetic minimal legacy fixture is canonicalized by favoriting/unfavoriting once before exact snapshots; this avoids mistaking omitted legacy fields versus encoded defaults for a data mutation. A first failure exposed that harness issue; a second selected the same-named search input instead of the result. Both failures remain retained. A focused pre-fix capture separately reproduced notes being stored but hidden from the logger.

Verification artifacts are under `.amp/in/artifacts/parity-implementation-05/`: `build-final-public.log`, `build-final-full-pack.log`, `android-final-tests.log`, `note-before.{log,xml,png}`, and `final/{result.json,apk-sha256.txt,*.xml,*.png}`. Final public/private build and lint, all 48 JVM tests, six public-build Android tests and the uninterrupted offline UI flow passed. Default/saved/invalid/recreated/cleared settings, Info, both new scopes and visible logger-note states were visually inspected, including settings at 1.3× text. Public captures contain generic asset fallbacks, not recovered media. Private-pack assembly is not a native media pass. Body/photo/chart implementations, media, parent smoke scripts and `PARITY.md` are untouched.

## Remaining acceptance backlog

1. **P1:** Edit Duplicate through the rich wizard, fresh identity, no mutation before Create, Back/Cancel and original-record preservation.
2. **P1:** Modified/Archived state and exact scope membership/ranking; obtain retained source outcomes before claiming parity.
3. **P1:** Equipment-weight and left/right rest override journeys, plus warm-up/working/final/exercise-transition policies and notifications.
4. **P1:** Actual recommendation exclusion once a source-evidenced generator exists. Source generation/progression/deload, populated analytics and the wider audit backlog remain open.

This unit starts from parent `e9cc9b38`, preserving the integrated body/photo/chart schema and decoder fixes. The parent's newer `cea57995` body presentation is not part of these tests and will be taken before the next unit. Changes are local and unpushed.
