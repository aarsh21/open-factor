# Fitbod strength-set CSV import

## Import flow

Open **Settings → Import Fitbod CSV** and select the local CSV in Android's document picker. This imports **completed workout history**, not program targets. The XLSX program-target importer remains separate.

The preview shows new/existing workout counts, source set counts, per-name exercise matching, workout dates and expandable source sets. Nothing is saved until every new source exercise name has an exact match, an explicit catalog choice, or an explicit skip, and the warning acknowledgement is checked. Choosing another match or changing skips clears that acknowledgement. Back/Cancel before confirmation changes no saved training data. Preview state is ephemeral; reopening or recreating the screen requires selecting the file again.

Only a unique normalized catalog name/alias is matched automatically, using the same conservative matching as spreadsheet imports. Ranked search results require explicit selection. Custom exercises participate; archived exercises do not. Review equipment and per-side semantics rather than selecting the nearest-looking name. If a suitable movement is missing, cancel and create a custom exercise first. Repeated exercise positions may map to the same movement without merging their source sets.

Explicit skips apply to that source name in every new workout. Workouts left with no included exercises are omitted; partially included workouts are saved with only the reviewed exercises. The preview reports the resulting workout count. Skipping everything disables saving. **A later reimport will not fill skipped exercises into an already imported workout.** Keep the source file and review skips carefully.

Confirmation performs one synchronized append using the existing durable store. It rechecks existing IDs and catalog availability at save time, publishes in-memory state only after persistence, and reports save failures. Existing history entries, active workouts, templates, programs, drafts, settings and Health Connect state are not replaced. Imported completed sets participate in normal history and training statistics. No provider/network write is triggered by this import. A confirmed save already executing when the screen is left may complete.

## Supported format

UTF-8 comma-separated CSV with these **18 unique headers**, in any order:

```text
date_local,date_utc,workout_name,exercise_name,set_number,reps,weight_kg,is_warmup,is_amrap,rest_seconds,set_duration_raw,distance_m,notes,workout_id,exercise_external_resource_id,exercise_position,circuit_position,set_id
```

A UTF-8 BOM, LF/CRLF line endings, a missing final newline, quoted commas, doubled quotes and quoted multiline notes are supported. Text is treated only as data; formulas or source text are never executed. Unknown/missing/duplicate headers, malformed quoting, ragged records and invalid values reject the whole document, rather than silently discarding rows.

| Source | Stored behavior |
| --- | --- |
| `workout_id` | Groups workouts; deterministic `fitbod-workout:`-prefixed history identity |
| `date_utc`, `date_local` | Both must be offset-aware ISO timestamps for the same instant; rows of one workout must agree on that instant |
| `workout_name` | Retained; blank becomes `Fitbod workout` |
| `exercise_position` | Determines exercise order (`0…99`); one position must have consistent name, external ID and circuit position |
| `exercise_name` | Requires reviewed catalog match or explicit skip |
| `exercise_external_resource_id` | Validates source exercise grouping; never treated as an Open Factor catalog ID |
| `set_id` | Unique throughout the file; deterministic `fitbod-set:`-prefixed identity |
| `set_number` | Orders sets within a source position; unique per position, integer `1…9999` |
| `reps` | Completed actual repetitions, integer `1…9999` |
| `weight_kg` | Completed actual load in kilograms, finite `0…5000`; recorded zero is retained |
| `is_warmup` | Completed set's warm-up flag; `True`/`False` (case insensitive) |
| `is_amrap` | `True`/`False`; true is persisted in exercise notes by source set number, **not** as RIR/failure/set kind |
| `rest_seconds` | Blank or integer `0…1800`. A consistent rest becomes the exercise rest value; varying rests are retained per source set in notes, leaving the exercise rest unspecified |
| `notes` | Retained in exercise notes, labeled by source set number |
| `circuit_position` | Blank or integer `0…99`; retained in notes, **not** converted to a superset |
| `set_duration_raw`, `distance_m` | Must be blank for this strength-only format; populated values reject the document |

Workouts are ordered newest first. Every imported set is completed, with actual weight/reps and warm-up state. RIR, targets, assistance, progression, program links and unprovided side/link structure remain unset. A chosen exercise must support reps and the recorded loads, and must not require absent metrics. Assistance loads are never inferred from `weight_kg`. Per-side weights/reps are not multiplied, divided or reinterpreted; the preview warns about this when reviewing matches.

### Missing workout duration

The file supplies one timestamp for each workout, not start/end timestamps or elapsed workout time. Both local start and finish are set to that instant, yielding **zero minutes**, with a persistent workout note explaining the missing duration and preserving the source local timestamp. Summing rest seconds or counting sets would invent duration, so neither is done. History dates follow the device time zone; the original source offset is retained in the note.

### Repeat imports

Existing `fitbod-workout:<source ID>` entries are skipped at preview and again at save time. Their local corrections remain untouched. Deduplication is by source workout ID, not workout name/date; an unrelated local workout on the same date is not considered a duplicate. Reimporting a larger export appends only previously unseen IDs. Already imported workouts are not updated or merged, even if the source contains additional sets or previous import choices omitted exercises. Deleting an imported workout removes its deduplication identity, allowing a later import to add it again. Duplicate set IDs, set numbers or conflicting source rows within one file are errors, not silently deduplicated.

## Bounds and privacy

- 4 MiB maximum input, strict UTF-8 without NUL characters.
- 20,000 data records, 2,000 workouts, 100 exercise positions per workout, 100 sets per position.
- 18 columns; 4,096 characters per field, 160 characters per workout/exercise name.
- Source identities contain only ASCII letters, digits, hyphens and underscores, maximum 128 characters.
- Parsing and saving run off the UI thread; interrupted parsing aborts.
- No new parser dependency, broad storage permission, retained document access, source-file copy, extraction or app network request.
- The source CSV remains private and is not bundled or committed. Make a complete backup before importing and retain the source for unsupported metadata.

This is not a generic CSV importer or a guarantee of support for other Fitbod export schemas.

## Verification

`FitbodImportTest` covers actuals, offsets, missing duration, quoting/BOM/newlines/reordered headers, ordering, notes/AMRAP/rest/circuit retention, blank names, zero loads, stable identities, JSON round trips, state preservation, overlapping imports/local corrections, explicit choices/skips, incompatible metrics, malformed/conflicting records and resource bounds. Existing spreadsheet-import tests remain separate regression coverage.

The optional private sample test reads the supplied CSV through the production Kotlin parser and validates **290 workouts, 5,299 sets and 59 source exercise names**, full model/codec conversion and repeat-import deduplication using a synthetic exact-name catalog. That verifies conversion, **not correctness of real catalog movement matches**. Only unique exact names/aliases are automatic; other names require review.

```sh
OPENFACTOR_FITBOD_SAMPLE=/absolute/path/to/private.csv \
  ./openfactor/gradlew -p openfactor --offline :app:testDebugUnitTest \
  --tests 'org.openfactor.app.FitbodImportTest' \
  --tests 'org.openfactor.app.SpreadsheetImportTest' \
  -PopenfactorMediaMode=source
```

`FitbodImportJourneyTest` additionally checks the native Settings entry and document-action clearance, Back without saved changes, a synthetic confirmed import through the real ViewModel/store, durable reload and repeated-import deduplication. These cases passed in the original-static-media verification build on Android API26 and API35. They do not establish real document-provider interaction or correct movement selection for a full private-source import. Accessibility and persistence fault injection remain unverified.
