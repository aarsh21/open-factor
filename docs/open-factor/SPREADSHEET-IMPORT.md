# Local spreadsheet workout import

## Entry point and safety contract

Settings → Data → **Import spreadsheet** opens Android's document picker. Select an unencrypted `.xlsx` document. The app reads the selected stream on a worker dispatcher; it does not upload the workbook, request broad storage access, retain document permissions, extract ZIP members to disk, or copy the source workbook into app storage.

The preview contains source phases/days/weeks, literal rest targets, exercise matching, excluded fields and unsupported-target warnings. Reading or cancelling the preview changes no saved training data. Preview state is deliberately ephemeral: leaving the screen or recreating the activity requires selecting the source again. No partially reviewed import is restored automatically.

Before saving, the user must:

1. Choose **1–30 working sets per exercise**. The supported workbook has weekly rows, not a set-count column. No set count is inferred from the number of weeks or achieved-rep cells. This explicit count applies to all imported exercises; individual targets can be edited in Programs afterwards.
2. Resolve each source exercise name, or explicitly **Skip exercise**. Skipping applies to that exact source name throughout the workbook. A day cannot become empty through skipping.
3. Review the target/matching/omission warnings and check the acknowledgement.
4. Press **Append … new programs**.

A confirmed import appends one new **manual program per phase**, with new local identities. It does not edit existing programs or templates, change the active program, replace an unsaved program draft, start a workout, or create completed history. Reimporting the same workbook intentionally creates additional independent copies; this is not a synchronization/deduplication feature. Open Workout → Programs to inspect, edit, activate or start an imported program. Programs are used instead of flattened templates because their existing weekly-cycle model can retain distinct weekly rep/RIR targets.

All phases are validated before a single synchronized storage update. The append revalidates catalog availability and rejects identity collisions. Persistence failures are reported as failures; the in-memory state is published only after the existing store successfully saves. A confirmed save already executing when the screen is left may complete; cancelling a *preview* never initiates a save.

## Exact supported layout

This first bounded implementation supports the supplied phase-oriented workbook layout, not arbitrary fitness spreadsheets:

- Worksheets named `Phase 1`, `Phase 2`, or `Phase 3` (case insensitive).
- Other sheets, including **Algorithms** and **Exercise Videos**, are listed as ignored and never treated as routines. Chart/formula footers beginning with `ESTIMATED 1RM` are ignored.
- Workout sections begin with `DAY <number> <optional title>`.
- A table header contains `EXERCISE`, `WEEK`, `REPS`, and `RIR`. Columns are located by headers rather than hard-coded positions. Weight and achieved-rep columns may exist but are not imported.
- An exercise name begins on its week-1 row. Subsequent rows with a blank name, `Video Tutorial`, or a recognized rest label continue that exercise's weekly targets.
- Literal rest rows use forms such as `2-MIN REST` or `60-SEC REST`, in the exercise-name column. The rest value is attached to all weeks for that exercise; conflicting values are rejected. Missing rest remains unspecified rather than inheriting another phase's prescription.
- Every exercise in a phase must contain exactly weeks `1…N`, for the same `N` in `1…52`. Missing/duplicate weeks are errors, never copied or guessed.
- Each phase becomes a program with `N` weekly cycles and only its listed workout days. No seven-day schedule or additional rest days are fabricated.
- Inline strings, shared strings, and shared rich-text strings are supported. XML members must be UTF-8.

Literal integer targets (including Excel numeric forms such as `9.0`) and rep ranges such as `6–8` / `6-8` are retained as program rep targets. Valid RIR is `0…10`; literal rest is `0…1800` seconds. The chosen set count repeats each week's prescription into that week's working-set targets. Starting an imported day uses the existing program launcher: actual weight, actual reps, actual RIR and completion remain blank/unperformed.

### Formula handling

**No formula is executed**, including workbook-defined names, lookup algorithms, macros, external links or hyperlinks. The importer does not implement a spreadsheet calculation engine.

Some supplied rep targets and exercise names are formula cells. Their *stored cached result* is accepted as plain data, validated exactly like a literal, and called out in preview as potentially stale. Formula-based weeks are rejected. A formula rep/RIR target without a cached value is rejected with instructions to recalculate and save in a spreadsheet app first. Formula expressions and cached weight/progression/history values are not saved as performance.

### Exercise identity

Automatic matching requires one unique catalog name or alias after conservative case, accent and punctuation normalization. The catalog includes the user's custom exercises; archived identities are excluded. The importer does not silently choose a ranked/fuzzy result, erase equipment qualifiers, collapse different variants, or create custom exercises from source strings.

Unresolved or ambiguous names expose suggestions through the existing `ExerciseSearchIndex`, with an editable query. Selecting a candidate is an explicit user choice. The preview shows the chosen catalog name. Two source entries in one workout day cannot map to the same catalog identity. Numeric rep targets cannot be mapped to a duration-only exercise. If no appropriate identity exists, cancel and create a custom exercise first, or explicitly skip the source exercise.

## Important limitations / excluded data

- **AMRAP, duration and step targets are not representable in the existing program rep-target model.** The reader recognizes `AMRAP`, `<integer> sec` and `<integer> Steps`, displays the original source target and a specific warning, and leaves the numeric rep target blank. It does not convert `30 sec` to 30 reps, convert steps to reps, or infer an AMRAP number. Keep the original workbook for these targets: unsupported source labels are **not persisted as program metadata**. RIR is retained only for rep-based catalog matches; non-rep matches keep RIR blank. This is a reviewed partial target import, not lossless workbook conversion.
- Weight, achieved reps (regardless of whether populated), timestamps/start dates, difficulty controls, formulas, progression/increment rules, colors, hidden-row difficulty logic, videos and links are excluded. Visible and hidden exercise rows are both imported; the app does not guess an Easy/Hard selection.
- No historical sessions are imported. Workbook weights are neither taken as logged actuals nor inferred as future load targets.
- No calendar dates, training schedule, rest days or phase activation order are inferred. Phases remain separate inactive programs.
- No CSV, XLS, XLSM, encrypted workbook, generic tabular layout, merged-cell inference, or formula recalculation support. This is separate from Open Factor JSON backup restore.
- No guarantee of exact catalog matches for every source name. Variant ambiguity requires review.

## Resource and XML boundaries

No added parser dependency, network access or external installation is required. The reader uses bounded `ZipInputStream` and SAX parsing:

| Resource | Bound |
| --- | --- |
| Selected compressed document | 4 MiB |
| ZIP members | 128 |
| Each expanded member | 2 MiB |
| Total expanded members (including ignored members) | 12 MiB |
| Worksheets listed | 20 |
| Rows per parsed phase worksheet | 2,000 |
| Cells per parsed phase worksheet | 50,000 |
| Columns per parsed phase worksheet | 128 |
| Shared strings | 20,000 |
| Cell/shared-string text | 4,096 characters |
| XML nesting / element count / attributes per element | 32 / 200,000 / 32 |
| Imported phases / days per phase / exercises per day | 3 / 14 / 100 |
| Materialized planned sets per import | 20,000 |

Absolute/traversing ZIP paths, duplicate ZIP members, unsafe worksheet relationship targets, external worksheet relationships and malformed references are rejected. ZIP members never become files. Before XML parsing, strict UTF-8 decoding and DTD/entity-declaration rejection prevent encoded declaration bypasses; external general/parameter entities are disabled and the entity resolver rejects external resolution. Namespaces, cell references, literal/cached target values, week completeness and import identities are validated. The security contract does not depend on Android exposing a Xerces-only parser feature.

## Verification

Focused JVM coverage is in `SpreadsheetImportTest`: synthetic in-memory XLSX fixtures, target/cycle materialization, blank actuals, append-only state preservation/collision handling, ambiguous matching, explicit skips, cached-formula warnings, invalid/missing targets, shared strings, DTD/external references, traversal, compressed expansion bounds, rows/cells/text/entries and XML depth.

Run from the repository root with a configured JDK 17 and Android SDK:

```sh
./openfactor/gradlew -p openfactor --offline :app:testDebugUnitTest \
  --tests 'org.openfactor.app.SpreadsheetImportTest' \
  -PopenfactorMediaMode=source
```

An optional private-source smoke test is enabled only by an explicit environment variable:

```sh
OPENFACTOR_SPREADSHEET_SAMPLE=/absolute/path/to/private.xlsx \
  ./openfactor/gradlew -p openfactor --offline :app:testDebugUnitTest \
  --tests 'org.openfactor.app.SpreadsheetImportTest' \
  -PopenfactorMediaMode=source
```

The supplied workbook was inspected with isolated Python stdlib ZIP/XML reads (no extraction or formula evaluation), then read by the production Kotlin parser through that optional test. The final focused run passed **37 tests**: 14 spreadsheet-import tests, 16 existing program tests and 7 existing template tests. Import coverage includes parsing all 3 phases / 15 workout days / 75 exercise occurrences and materializing 1,125 planned sets with an explicitly chosen count of 3 and a synthetic exact-identity catalog. That synthetic catalog checks model conversion, **not correctness of actual catalog matches**. The first two focused runs exposed cached formula rep/name handling; those were corrected before the successful runs. The source-only app and Compose integration compiled as part of the run.

No copyrighted workbook bytes or extracted source text were added to Git. During final lead integration, the real Android document picker on API 26 selected the supplied workbook, the preview showed three programs / 15 days, and Cancel returned without confirming an append. The actual catalog produced 44 distinct source names, with 33 requiring explicit choices; a full real-catalog Outlift append and correctness of all chosen movement variants are **not** claimed. The footer was corrected to clear both floating navigation and system bars, catalog lookups were memoized, and native `SpreadsheetImportJourneyTest` coverage passed for footer clearance, Back safety and platform XML parsing. See [the final working-tree verification record](PERFORMANCE.md).

TalkBack, interrupted provider reads, persistence fault injection, full actual-catalog matching and physical-device performance remain unverified. No release artifact was published.
