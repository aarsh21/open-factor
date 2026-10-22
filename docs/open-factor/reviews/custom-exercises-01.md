# Custom exercise creation, 22 October 2026

The four-field custom form is replaced by the retained five-step journey. This closes the observed form inventory, not full source exercise-management parity.

## Source evidence and unresolved policies

`S` is the private retained source directory named in [the baseline audit](whole-app-parity-2026-10-22.md). `S/feature-tour/indexed/14-exercise-tools.mp4` supplies these observations at clip-relative seconds:

- 24 and 30: name, initially unselected metrics, Compound Upper, Bilateral, and the metric-choice sheet.
- 36: upper and lower muscles, primary/secondary counts, and Skip.
- 42 and 48: Bodyweight Only, support, and alternate resistance.
- 50 through 58: ROM and stability ratings, required bodyweight contribution, joint actions, alternate names with a 300-character limit, and the review screen.
- 64: final-field warning and Create. The source tour does **not** execute Create.

The source video hash is unchanged before and after inspection. Private decoded frames and hashes remain under `.amp/in/artifacts/private-source-custom/`. None are application assets or published review images.

Source muscle-tap cycling, metric-pair restrictions, the full type/laterality/joint/equipment menus, and post-Create persistence remain unobserved. The local wizard cycles unselected → primary → secondary and rejects duplicate metric families. Its classification choices use catalog Type/Region strings. These are explicit local policies, not recovered source rules. The captured 75% contribution does not establish a default, so the local field requires an explicit value.

## Local contracts

- [CustomExercise.kt](../../../openfactor/app/src/main/java/org/openfactor/app/CustomExercise.kt) follows basics → muscles → equipment → final details → review. Back retains edits, sheet Cancel discards provisional equipment edits, and Create is the only persistent write. Android saved state retains the draft after background process death.
- [CustomExerciseData.kt](../../../openfactor/app/src/main/java/org/openfactor/app/CustomExerciseData.kt) separates incomplete drafts from saved exercises. Skipped muscles remain empty. Resistance and support each store alternative groups of required equipment IDs, rather than display names alone.
- [Data.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Data.kt) round-trips metrics, aliases, muscles, properties, contribution, and equipment requirements. Old minimal custom backups still load. Body/photo records and existing metric fields are unchanged.
- [Gyms.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Gyms.kt) applies custom equipment requirements to gym availability. [Exercises.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Exercises.kt) displays saved alternatives and support. [MainActivity.kt](../../../openfactor/app/src/main/java/org/openfactor/app/MainActivity.kt) preserves the requesting draft, session, or program context.

This unit does not implement modified/archived/deprioritized exercise states, source ranking, independent left/right logging, or persistent-weight sequencing. It preserves the chosen navigation and media presentation. It does not claim pixel-identical source controls or recover unexecuted source outcomes.

## Verification and delivery

Public and private-pack build/lint checks passed. All 45 JVM tests and five Android tests passed. The two added JVM cases check rich backup/logging/equipment behavior and explicit-required-input/skip behavior. The metadata-loss regression failed before the codec change.

`tooling/android_custom_exercise_smoke.py` passed uninterrupted offline on disposable `emulator-5584`. It exercised all five steps, invalid-input recovery, sheet Cancel, Back, process recreation before Create, alias search, gym exclusion, contextual Add, assistance/duration logging, restart, and unchanged body/photo records. Captures were inspected, including the fixed expanded equipment footer. The bodyweight preview uses the latest record not later than today, excluding the synthetic future record.

Private local artifacts are `.amp/in/artifacts/parity-implementation-03/`: `build-03.log`, `android-tests.log`, and `verified/{result.json,state.json,apk-sha256.txt,*.png,*.xml}`. Earlier unsuccessful UI attempts remain recorded; only `verified/result.json` is the complete custom-flow pass. The updated composition regression also passed uninterrupted. `composition-regression/result.json` verifies draft/session/program custom creation and contextual Add, picker query restoration, explicit Start, restart, and unchanged body records with the new wizard. The UI fixtures contain no attached photos; the five Android tests separately cover photo backup/storage.

The unit is based on local metric/photo commit `635ced02`, not a new remote baseline. Parent local main separately includes its logger-width and empty-cache fixes; neither file is overwritten here. Integration must preserve those fixes. No source media, body/photo implementation, parent smoke scripts, or `PARITY.md` changed. Work is local and unpushed.
