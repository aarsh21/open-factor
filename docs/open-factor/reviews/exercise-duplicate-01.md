# Editable exercise duplication, 22 October 2026

Edit Duplicate opens an unsaved copy in the existing five-step wizard. Explicit Create assigns a new custom ID. Back/Cancel do not change the original exercise, overrides, training entries or body data. This unit preserves parent `fbd6d53c`, including Full Body routes and the app-night sheet fix.

## Evidence and policy boundary

The source row and its explanation, “To edit all available exercise properties, create a duplicate,” appear in the retained chapter-03 frame at 116 seconds. Source custom creation fields/review are documented in [the custom-wizard review](custom-exercises-01.md). The source duplicate action and Create outcome were not executed. Copy naming, editor prefill, media/default/history inheritance and return behavior below are local contracts, not accepted one-to-one source outcomes.

The copy retains metrics, laterality, primary/secondary muscles, resistance/support alternative groups, ratings, bodyweight contribution, joint actions, aliases and available instruction text. It does not copy demonstration media, history or per-exercise overrides. A source instruction asset is read without changing the original file. Existing property values remain editable; bodyweight percentages preserve decimal precision rather than truncating to whole percentages. Create uses the ordinary custom-creation/contextual-Add route.

Missing type/region or unresolved legacy equipment requires an explicit choice rather than inventing upper/lower or bodyweight-only. Catalog equipment groups come from the authoritative equipment index, not the coarse family label. New normal custom creation retains its existing defaults.

## Implementation and observed defects

- [CustomExerciseData.kt](../../../openfactor/app/src/main/java/org/openfactor/app/CustomExerciseData.kt) builds the draft without mutating the original and rejects unresolved type/equipment at creation.
- [Gyms.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Gyms.kt) exposes the existing structured requirements without changing availability/filter behavior. Native coverage checks a barbell/plates compound requirement and five support alternatives.
- [CustomExercise.kt](../../../openfactor/app/src/main/java/org/openfactor/app/CustomExercise.kt), [ExerciseSettings.kt](../../../openfactor/app/src/main/java/org/openfactor/app/ExerciseSettings.kt) and non-body [MainActivity.kt](../../../openfactor/app/src/main/java/org/openfactor/app/MainActivity.kt) add the row, saved routing context and initial wizard data. No new body schema or media changes belong to this unit.
- Real catalog aliases reach 515 characters, above the existing 300-character creation limit. Copied over-limit text can now shrink incrementally; it is not silently truncated. Alias and instruction editors have bounded visible lines so long content cannot push their labels out of reach.
- **Known metadata gap:** the generated catalog omits Region, although raw source `regionTrained` resolves to Upper body/Lower body/Full body/Core. Catalog duplication therefore currently requests the missing type. This is recoverable factual projection work, not an unrecovered algorithm; it is prioritized next. Existing rich custom records already retain Region.

## Verification and remaining work

Public/private build and lint, 51 JVM tests and six public-build Android tests passed. Expanded [android_custom_exercise_smoke.py](../../../tooling/android_custom_exercise_smoke.py) passed uninterrupted on disposable emulator-5584, offline, after the final editor fix. Result: `.amp/in/artifacts/parity-implementation-06/final/result.json`; logs: `build-final-public.log`, `build-final-full-pack.log`, `android-final-tests.log` and `final.log` in that artifact directory.

The smoke retains the normal five-step/metric/gym/offline regression and adds duplicate prefill, discarded edits, no write before Create, actual process recreation, original/default/active preservation, fresh identity, independent contextual Add, missing-type recovery and real 515-character alias recovery/cancel. Early runs exposed a harness null-key assumption, an ineffective injected Ctrl+A, and an oversized multiline editor; all intermediate evidence remains retained. A resumed tail is not an aggregate pass.

Rendered captures `final/13-duplicate-prefilled.png` through `final/16-created-duplicate.png`, `final/18-catalog-missing-type.png` and `final/19-over-limit-alias-recovery.png` were inspected. Copy warning, equipment alternatives, review, created details, missing-type prompt and bounded four-line alias editor are readable without overlap. Screenshots establish layout only; persisted state assertions establish identity, unchanged original/overrides/active entries, no inherited media and unchanged body/photo data.

Remaining priorities are the source-facts Region projection; source-observed Modified/Archived behavior; equipment/left-right rest policies; and actual source duplicate/save outcomes. Generator/progression/deload and the wider audit backlog remain unaccepted. Nothing is pushed or released, and no recovered media is published or modified.
