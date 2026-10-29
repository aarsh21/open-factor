# Fitbod strength-set CSV import

## Import flow

Open **Settings → Import Fitbod CSV** and select the local CSV in Android's document picker. This imports **completed workout history**, not program targets. XLSX program-target import remains separate.

Reading and matching run off the UI thread with distinct progress messages. The preview resolves every source name by either a compatible catalog match or a preserved custom exercise; dozens of manual choices are no longer required to enable import. Nothing is saved before confirmation.

The compact preview shows resulting workout/set counts, existing-workout skips and the number of new custom exercises. **Review exercise matches** expands the choices. Source workouts start with five collapsed summaries; **Show more source workouts** adds ten at a time, and source workouts/sets can be expanded for inspection. The acknowledgement and Import/Cancel actions remain pinned above navigation. Reaching confirmation does not require scrolling through the entire history file. Changing a match or skip clears acknowledgement.

Back/Cancel before confirmation changes no saved data. **Cancel reading** aborts preparation, cancels the provider request and closes its input stream; a late provider result cannot replace a later preview. Provider reads time out after 30 seconds, and matching has a separate 30-second bound. Timeout/failure gives a visible error without automatically retrying. Copy a slow cloud-backed document to Downloads if necessary. A provider returning zero bytes repeatedly cannot spin the parser forever: a single-byte read makes progress or detects EOF. Preview state is ephemeral; recreating/leaving the screen requires selecting the file again.

## Automatic matching without losing uncertain exercises

1. Unique compatible normalized names/aliases win first. The shared search vocabulary handles plurals and common abbreviations.
2. Fitbod-specific normalization handles reordered words, leg/hamstring curls, shoulder/overhead presses, skull-crusher terminology and V-bar pulldowns.
3. A closest match requires strong token coverage, at most one bounded typo, a clear lead over competing identities, compatible actual metrics and explicit equipment agreement. Popularity or arbitrary ID order never breaks a semantic tie. Added unilateral, grip, assistance and other protected movement qualifiers are not silently guessed.
4. If the match is ambiguous, unavailable or incompatible, a **custom exercise preserves the original Fitbod name and Weight/Reps actuals** instead of blocking the import or selecting an unrelated variant. Explicit equipment words are retained; muscle targets, bodyweight contribution and missing movement facts are not inferred. Those custom exercises may need muscle/equipment editing before muscle-specific analytics can classify them.

You can **Change match**, **Keep original** or explicitly **Skip exercise** for any source name. Archived exercises do not participate. Previously preserved custom exercises are reused, including after a local rename through retained Fitbod-source provenance. Equivalent normalized source spellings in the same import share one preserved identity. Original source names are also retained in imported exercise notes, so automatic matches remain auditable.

Custom exercises and their referencing history are committed in **one synchronized durable update**. Only custom identities actually used after matching/skips are appended. Changing a choice to a catalog exercise, skipping a source or reimporting already saved workouts does not add unused/duplicate custom exercises. The save rechecks catalog availability and preserves existing history, active workouts, programs, templates, drafts, settings and Health Connect state. In-memory state publishes only after persistence succeeds. A confirmed save already executing when the screen is left may complete.

Skips apply to that source name in every new workout. Empty workouts are omitted; partially included workouts contain only reviewed exercises. **Reimporting does not fill previously skipped exercises into an already imported workout ID.** Keep the original source and review skips before saving.

## Supported format

UTF-8 comma-separated CSV with these **18 unique headers**, in any order:

```text
date_local,date_utc,workout_name,exercise_name,set_number,reps,weight_kg,is_warmup,is_amrap,rest_seconds,set_duration_raw,distance_m,notes,workout_id,exercise_external_resource_id,exercise_position,circuit_position,set_id
```

A BOM, LF/CRLF, a missing final newline, quoted commas, doubled quotes and quoted multiline notes are supported. Text/formulas are data only and never executed. Malformed, missing/duplicate/unknown headers, ragged records and invalid/conflicting values reject the entire document rather than silently discard sets.

| Source | Stored behavior |
| --- | --- |
| `workout_id` | Groups workouts; deterministic `fitbod-workout:`-prefixed history identity |
| `date_utc`, `date_local` | Offset-aware ISO timestamps for the same instant; rows of one workout must agree |
| `workout_name` | Retained; blank becomes `Fitbod workout` |
| `exercise_position` | Exercise order (`0…99`); each position must have consistent name, external ID and circuit position |
| `exercise_name` | Compatible catalog match or preserved custom; original name also retained in notes |
| `exercise_external_resource_id` | Validates source grouping, never treated as a local catalog ID |
| `set_id` | Unique throughout the file; deterministic `fitbod-set:` identity |
| `set_number` | Set order, unique within a source exercise position; integer `1…9999` |
| `reps` | Completed actual repetitions, integer `1…9999` |
| `weight_kg` | Completed actual kilograms, finite `0…5000`; zero retained |
| `is_warmup` | Completed set's warm-up flag; case-insensitive `True`/`False` |
| `is_amrap` | True is retained in notes by source set number, not inferred RIR/failure/set kind |
| `rest_seconds` | Blank or integer `0…1800`. Consistent rest becomes the exercise value; varying rests are retained in notes with no guessed single rest |
| `notes` | Retained in exercise notes by source set number |
| `circuit_position` | Blank or integer `0…99`; retained in notes, not converted to supersets |
| `set_duration_raw`, `distance_m` | Must be blank; populated duration/distance sets reject this strength-only import |

Workouts sort newest first. RIR, targets, assistance, progression and unprovided side/link structure remain unset. No weight doubling, per-side conversion, bodyweight contribution or missing metric is invented.

### Missing workout duration

The file has one timestamp per workout, not start/end or elapsed time. Start and finish use that instant, yielding **zero minutes**, with an explanatory note and the original local timestamp. Rest values/set counts are not used to invent duration. Display dates follow the device time zone.

### Repeat imports

Existing Fitbod workout IDs are skipped at preview and save, preserving local edits. Deduplication is by source ID, not name/date. Already saved workouts are not updated or merged, even if new source sets or formerly skipped exercises are present. Deleting an imported workout removes its deduplication identity, allowing a later import to add it again. Duplicate set IDs/numbers or conflicting rows inside a file are errors.

## Bounds and privacy

- 4 MiB strict UTF-8 input, no NUL characters.
- 20,000 data records; 2,000 workouts; 100 exercise positions per workout; 100 sets per position.
- Automatic preview resolves at most 1,000 distinct exercise names per import.
- 18 columns; 4,096 characters per field; 160 per workout/exercise name.
- Source identity tokens: ASCII letters/digits/hyphens/underscores, at most 128 characters.
- No broad storage permission, retained document access, source-file copy into app storage or app network upload. The input stream is closed after reading/cancellation.
- This is not a general-purpose CSV importer. Keep the original CSV and a full backup before importing.

## Verification

`FitbodImportTest`, `FitbodExerciseMatcherTest` and `FitbodImportReaderTest` cover actuals, CSV syntax/resource boundaries, metric/equipment/variant safety, ambiguity, bounded typos, custom preservation/reuse, atomic append/skips, local renames, zero-byte readers, stalled providers, cancellation, no automatic retries, codec persistence and deduplication. Native journeys cover the Settings/document action, Back safety, durable catalog/custom imports, and pinned confirmation on a narrow enlarged-text viewport.

The explicitly supplied private CSV resolves **59 names: 13 normalized name/alias matches, 14 closest compatible matches, 32 preserved custom exercises** against the real catalog. A native verification build on API35 selected the full file through Android's document picker and saved **290 workouts / 5,299 sets**. Private-store inspection verified every source set's kilograms, reps, warm-up and completion, unchanged non-history data, and the same records after restart. This does not certify each exercise identity choice or every provider/device. The private CSV, private history and captures containing its workout names are not published.

```sh
OPENFACTOR_FITBOD_SAMPLE=/absolute/path/to/private.csv \
  ./openfactor/gradlew -p openfactor --offline :app:testDebugUnitTest \
  --tests 'org.openfactor.app.Fitbod*Test' \
  --tests 'org.openfactor.app.SpreadsheetImportTest' \
  -PopenfactorMediaMode=source
```

These Fitbod matching changes do not relax the separate XLSX program importer's unique-literal-match policy. TalkBack certification, arbitrary cloud-provider cancellation behavior and persistence fault injection remain unverified.
