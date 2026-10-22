# Full Body records and visual body fat

Full Body opens as a dated bottom sheet from the Measurements history or the
Log Measurements shortcut. The header has Close, a date selector, and Delete
for a saved record. Save stays below the scrollable fields and above the keyboard.
The form uses filled charcoal inputs with external labels. Body Fat precedes
Upper Body, Arms, and Legs.

## Observed source behavior

The retained `source-body-metrics.xml` and `10-body-data.mp4` show a separate
Visual Body Fat input and Reference action. Around 39 seconds, Reference opens
a sheet with nine male torso illustrations and these labels:

| Original asset | Captured label |
| --- | --- |
| `M-3pct.svg` | 3–4% |
| `M-6pct.svg` | 5–7% |
| `M-10pct.svg` | 8–12% |
| `M-15pct.svg` | 13–17% |
| `M-20pct.svg` | 18–23% |
| `M-25pct.svg` | 24–29% |
| `M-30pct.svg` | 30–34% |
| `M-35pct.svg` | 35–39% |
| `M-40pct.svg` | 40% + |

The capture does not show reference-card selection, a completed Full Body Save,
or populated history. A prefilled source percentage does not establish a
default or an estimator. No calculation or card-to-percentage rule is recovered.
Source notification interruptions are excluded from behavioral evidence.

Around 80.9 seconds, the tour dismisses Full Body to the same Levels page that
remained behind the sheet. Local quick-log sheets preserve their caller on
dismissal. A sheet opened from measurement history returns to Body Data.

## Local record policy

`BodyMeasurementRecord.visualBodyFatPercent` stores a nullable, manually entered
percentage independently of `ScaleWeightRecord.bodyFatPercent`. Either visual
body fat or at least one circumference is required. Empty means absent, while
0% is a recorded value. Clearing visual body fat retains circumference values.

Local limits are 0–100% inclusive for visual body fat and greater than 0 through
1000 cm for each circumference. These are application limits, not source rules
or medical advice. New forms start empty. Reference is read-only and never
changes the input or calculates a percentage.

The existing stable-ID and calendar-date rules apply. Same-date records coexist.
History sorts by date and shows visual body fat before circumference summaries.
An unchanged edit preserves the stored precision despite rounded display.
Close, Back, or sheet dismissal discards unsaved changes. Delete requires
confirmation. These local outcomes do not claim unobserved source equivalence.

Data Visibility hides circumference inputs without removing their draft or saved
values. Saving a different visible field preserves hidden values at their stored
precision. Visual Body Fat and the scale-weight fields remain visible because
they are source Default metrics. History, raw charts, and complete backups do
not apply the visibility filter. Circumferences remain in cm; the separate height
unit preference does not establish a circumference conversion rule.

Complete JSON backup includes the optional `visualBodyFatPercent` key in each
body-measurement record. Older records without it remain readable. Invalid
percentages reject the whole restore before saved training, measurements, or
photos change. The separate raw Visual Body Fat chart plots recorded values
under the [documented range policy](BODY-CHARTS.md).

## Private reference assets

Public source builds include the captured labels and an explicit message that
illustrations are not bundled. No substitute art or generated bodies are used.
The optional Gradle property `openfactorBodyReferenceDir` adds a supplied assets
root. That root contains `body-fat/` with the nine original SVG filenames above.
Only builds with all nine files show illustrations. A decoder failure displays
Image unavailable instead of silently hiding it.

The recovered originals remain private and ignored. This property grants no
redistribution rights. The immutable exercise reference pack is unchanged.

## Verification interface

`tooling/android_full_body_smoke.py` accepts `--serial emulator-N`, `--output`,
and optional `--illustrated` for a private build. It rejects physical serials.
The flow disables connectivity, enters all 18 asymmetric circumference values,
and checks visual-only records, zero, blank, invalid input, dates, precision,
reference dismissal, enlarged text, keyboard clearance, editing, and deletion.
Native document export and restore checks cover Cancel, invalid-restore
atomicity, restart, and unchanged training and original private photo bytes.
The smoke restores network and font settings and leaves synthetic records.

`BodyDataTest` covers the optional backup field, separate scale and visual fat,
raw chart values, validation, and legacy records. `BodyDataStorageTest` checks
durability and complete restore with metric actuals and a workout draft present.

## Remaining source parity

No visual-fat estimator, source initialization rule, trend smoothing, or source
Save semantics are implemented or claimed. Reference art is absent from public
builds. The local reference has a title, explanatory copy, and Close control
that the captured source sheet does not establish. Native date and keyboard
controls, exact source typography and spacing, populated history, same-day
policy, and saved source outcomes remain different or unobserved. The
[photo editor](PROGRESS-PHOTOS.md) also uses a compact dated sheet.

Full Body reserves a 48 dp outside-dismissal strip below the top safe-drawing
inset. The previous percentage-height layout left only 110 px above the sheet
on emulator 5582, behind its 128 px status bar. The explicit inset keeps the
near-full-height form, scrollable fields, and keyboard-aware Save footer while
making the scrim reachable. This clearance is a local accessibility adaptation,
not a recovered source spacing value.
