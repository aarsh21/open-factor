# Gym inventory editor contract

This change adds category bulk selection and a transactional, per-unit weight
editor. It does not establish complete source parity.

## Retained source evidence

The read-only `inspection/private/feature-tour/indexed/11-gym-profiles.mp4`
shows category **Deselect All**, weight enable checkboxes, **Set Qty**, quantity
minus and plus buttons, **Remove Qty**, a kg/lb switch, and an expanded gym icon
chooser. Setting a quantity displays 2. The weight editor has a plus action and
**Done**. Source save, cancellation, unit-list identity, quantity boundaries,
and load-calculation effects were not established by that tour.

`source-gym-edit.xml` shows kilogram summaries for Bumper Plates, Dumbbells,
Kettlebells, Medicine Ball, and Weight Plates. `icon-default-recovery/REPORT.md`
identifies these as saved-profile observations, not untouched factory defaults.
The catalog supplies literal, asymmetric kg/lb selections for equipment such as
Barbell: 20, 15, 7 kg versus 45, 33, 15 lb.

The catalog has 297 equipment entities. Its Everything, Warehouse, and Home
memberships remain 297, 43, and 22 rather than the runtime's 292, 41, and 21.
No entity was deleted to match a count. No source phone, source application,
or shared private asset pack was modified. Original captures and assets are not
part of this change.

## Local behavior and durable data

- Category **Select All** and **Deselect All** update the entire category,
  including rows hidden by search. Other categories and unknown IDs survive.
  Equipment toggles and category changes save immediately.
- A summary uses the selected unit's enabled rows, including catalog selections
  before the first edit. The five observed kilogram arrays are local starter
  selections where the catalog has no literal array. They are not a recovered
  default generator. Missing pound defaults remain empty instead of being
  invented by converting those arrays.
- The weight editor keeps independent kg and lb drafts. Unit changes do not
  change the global preference or convert one new list into the other.
  **Save** commits both drafts. **Cancel**, header Back, and system Back discard
  both drafts. Saved-state recreation retains the open item, unit, and drafts.
- Each row has a finite, non-negative weight, an enabled flag, and an optional
  positive integer quantity. **Set Qty** starts at 2. Minus stops at 1, plus
  stops at `Int.MAX_VALUE`, and **Remove Qty** removes the stored quantity.
  Zero weight is valid. Add rejects invalid numbers and duplicate values.
  Deleting every row saves an explicit empty list, not a reset to defaults.
- Enabled state controls which rows appear in the gym summary. Quantities
  persist as inventory metadata. Neither field changes exercise availability,
  logger targets, suggested loads, or plate calculations. Those source rules
  remain unrecovered.
- Availability still uses equipment membership and the existing alternate
  resistance and support requirement groups for catalog and custom exercises.
- `GymProfile.weightSelectionsKg` and `weightSelectionsLb` map equipment IDs to
  `{value, enabled, quantity}` rows. `GymCodec` includes both maps in ordinary
  storage and full backups. Invalid quantities, negative or non-finite weights,
  duplicate rows, and malformed map types reject restore before saved data changes.
- Legacy `weightsKg` remains unchanged. If a per-unit map has no entry, that old
  list remains the fallback in kg and converts for lb, preserving the previous
  application's meaning. An explicit empty new entry overrides the fallback.
  Unknown equipment IDs and icon keys round-trip without catalog filtering.
- The chooser adds Material building variants while retaining old icon keys.
  These are public library approximations, not recovered original glyphs or a
  claim of exact source icon equivalence.

## Verification

The source-only APK builds with JDK 17, one Gradle worker, and a 1536 MB heap.
All 55 JVM tests pass, including four inventory contract tests. Lint reports
zero errors and nine warnings. The APK contains only the three catalog JSON
assets, with no private media, fonts, equipment art, or reference images.
The build runs `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug
:app:assembleDebugAndroidTest` from `openfactor/`. The APK's SHA-256 is
`2a7d6c89d2531ffe886d727e160f9d4438348f5eff6d95c98d25746ef67f6ab8`.

All seven Android tests pass on allocated disposable emulator-5584, including
`GymStorageTest`. That test preserves a nonempty active workout, history,
workout draft, saved program, program draft, exercise defaults, scale weight,
measurements, and original photo bytes through inventory edits, a new view
model, full export and restore, invalid-restore rejection, and legacy restore.

The native interaction checks pass offline on that same source-only APK:

- Search-hidden category bulk changes preserve unrelated and unknown IDs.
  A custom exercise with alternate resistance and required support equipment
  changes availability when the category membership changes.
- Asymmetric kg and lb drafts retain enabled state and quantity. Minus stops
  at 1. Save persists both lists. Cancel and Back discard edits. Rotation,
  font-scale recreation, and a background process kill retain open drafts.
- Deleting all pound rows persists an explicit empty list. Native document
  export and restore recover the asymmetric lists. Cancelling restore leaves
  data unchanged. A quantity-zero backup is rejected without changing data.
- New-gym name, preset, and icon drafts survive font-scale recreation.
  Cancel and Back do not create a profile. Enabled and starter summaries
  display the expected values.

Normal and 1.3-times font-scale captures of the affected dialogs were inspected.
Weight rows, quantity controls, unit chips, save actions, and icon choices have
no relevant overlap or clipping. The enlarged background gym action row remains
horizontally scrollable, with Duplicate partly outside the initial viewport.

These checks ran in resumed phases, not one uninterrupted aggregate run. The
retained logs include driver failures and an executor replacement. Corrected
selectors and resumed checks pass without application changes. The installed
APK hash was checked again after the replacement.

The original 28,869-byte preferences file was restored and byte-compared.
The original fixture had no referenced photos. The photo directory was empty
after restoration, but no claim is made about a pre-run orphan-file snapshot.
Font, rotation, and network settings were restored. Temporary external files
were removed. The app was force-stopped, and emulator-5584 was released to the
lifecycle owner through the parent thread.

Build and instrumentation logs are in the locally excluded
`.amp/in/artifacts/`. Native results, screenshots, fixture data, and restoration
evidence are in `.amp/in/artifacts/gym-ui/`. They verify this implementation,
not the original source application's save or calculation algorithms.

## Remaining source gaps

The retained source does not establish quantity or enabled-state effects on
availability or load calculations. Quantity therefore remains metadata.
Independent unit lists and transactional Save and Cancel are explicit local
contracts, not a claim that the source stores its data identically. The five
observed kilogram arrays are starter selections from a saved profile, not a
recovered factory generator. Material icons approximate the observed chooser.
Catalog snapshot count differences remain unresolved, with every ID retained.
