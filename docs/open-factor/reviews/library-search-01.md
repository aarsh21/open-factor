# Library search and resistance filtering, 22 October 2026

New queries and filter changes now reveal the first result. Returning from exercise detail with unchanged filters retains the reading position. Resistance filtering uses equipment IDs and alternative resistance groups instead of comparing an equipment-family label.

## Evidence and limits

The retained source chapter `S/feature-tour/indexed/03-exercise-library.mp4` shows a Resistance sheet with equipment entities, category headings, illustrations, circular selectors, a close control, and a bottom name filter. `S` is defined in [the baseline audit](whole-app-parity-2026-10-22.md). Private samples and unchanged input hashes are under `.amp/in/artifacts/private-source-library/`. The broad sheet inventory appears around 40–84 seconds; the sampled contact sheet does not establish individual selection outcomes.

The parent reported a hidden exact Chin-Up search result after scrolling. The synthetic pre-fix run reproduced that failure independently. This establishes an Open Factor UX defect, not recovered source query-reset behavior. Source ranking, multiple-selection rules, selection dismissal, and reset/persistence outcomes remain unobserved.

Local policy selects one resistance entity, applies it immediately, and offers All Resistance to clear it. Membership includes an entity in any resistance alternative and excludes support-only equipment. Gym availability remains an additional independent constraint. Explicit empty custom resistance groups match Bodyweight Only; old minimal custom records retain name-based fallback matching. These local contracts are not claimed as recovered source algorithms.

## Implementation and acceptance

- [Exercises.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Exercises.kt) owns the saved list state and last-applied filters. Only changed query/filter values reset scrolling. The source-observed searchable Resistance sheet replaces the coarse equipment chooser. Nuvio navigation and the floating search remain unchanged.
- [Gyms.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Gyms.kt) resolves resistance membership separately from support requirements. The sheet lists entities used as resistance by the current catalog/custom exercises.
- [TrainingFlowTest.kt](../../../openfactor/app/src/androidTest/java/org/openfactor/app/TrainingFlowTest.kt) extends the existing catalog contract with compound/alternative/custom/bodyweight membership and support-exclusion checks.
- [android_library_smoke.py](../../../tooling/android_library_smoke.py) uses synthetic records on disposable `emulator-5584`. It checks the new-query regression, unchanged-query detail return, equipment search/dismissal, alternative membership, empty intersections, clear, real background process recreation, unchanged persisted data, and no default network.

The working baseline includes exact parent commit `4733bb33`, including charts, photo fields, logger widths, empty-cache recovery, catalog-muscle fallback, and custom contribution validation. This unit does not edit Data.kt, MainActivity.kt, body/photo/chart modules, media, parent smoke scripts, or `PARITY.md`.

## Verification and remaining gaps

Public and private-pack build/lint checks and all 47 JVM tests passed. All five Android tests passed on the public build. The final library UI run passed uninterrupted; modified sheet and empty/filtered/default/recreated states were visually inspected. Public screenshots contain generic asset fallbacks, not recovered media. Private-pack assembly/lint does not establish a private-pack native media pass.

Local evidence is `.amp/in/artifacts/parity-implementation-04/`: `before-02.log` with the reproduced failure, `build-final-public.log`, `build-final-full-pack.log`, `android-final-tests.log`, and `final/{result.json,apk-sha256.txt,*.png,*.xml}`. Earlier unsuccessful UI attempts are retained. Their smoke issues were a navigation text locator, an Android-edge Back gesture, and selecting the search input instead of the same-named equipment row. A real low-contrast fallback icon and missing category inset were corrected and reinspected.

Lifecycle scopes and source ranking remain open under audit B08. No archive policy or generator/progression algorithm was inferred. The source exercise-settings frame at 116 seconds additionally establishes Info, Exercise Note, Do Not Recommend, Edit Duplicate, and default rest controls, but not their saved outcomes. Those remain separate acceptance work. Changes are local and unpushed.
