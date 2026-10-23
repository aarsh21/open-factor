# Smart-generation recovery

`materializeProgram` converts already selected exercise templates into a seven-day, seven-normal-week `TrainingProgram`. `allocateProgramVolume` recovers the earlier weekly time-budget stage. Neither function implements the complete source smart generator, and neither has a smart-generation UI caller. Exercise selection and day distribution remain incomplete.

## Input boundary

The function requires a resolved `ProgramGoal`, a resolved `ProgramPeriodization`, and two through six ordered `SelectedProgramDay` values. Each selected exercise supplies an `Exercise`, allocated working-set count, and template role. Names, color, and gym identity are caller inputs. The function does not resolve a split preference or choose exercises.

The native service uses the record `({MuscleGroup? accessoryMuscle, Exercise exercise, int numSets, ExerciseTemplateType type})` after selection. The materializer takes only the facts that its target-construction stage reads. The lifecycle unit projects factual Movement Patterns into the catalog. `Exercise.movementPatterns` distinguishes absent facts from an explicit `None`. The materializer rejects absent Movement Patterns or Metrics instead of inferring them. It uses exact `Horizontal Pull` and `Vertical Pull` values and the existing `loggingMetrics().any { it.isReps }` classification, including per-side reps.

The public catalog now preserves source goal-specific descriptors and six recommendation levels in `generationFacts`. Exercise names, muscles, joint actions, and `searchBoost` are not substitutes for those fields.

Equipment, focus, session duration, and experience are not materializer inputs. The separate allocator accepts an explicit session-length choice, split, goal, frequency, warm-up setting, and category rest overrides. Neither function accepts a nullable preference that could imply a fabricated recommendation policy.

## Recovered behavior

Source identity is the retained ARM64 `libapp.so`, SHA-256 `870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`. Addresses below are static instruction addresses, not observed runtime calls. The retained partial Dart metadata and pinned Dart 3.13 serialization sources support the instruction traces. No successful Dart decompilation or new REA Evidence ID is claimed.

| Contract | Static evidence |
| --- | --- |
| Calendar positions for 2–6 workouts | `ecf9ac` through `ecfba0`; `ed3f70` maps null to rest and an index to the selected training day |
| Seven normal weeks | `ecfc48` through `ecfc7c` passes `numCycles=7` |
| Pull predicate | `ed4338` through `ed43ac`, helper `6d4280`, closure `6d4344`: any intersection with horizontalPull or verticalPull |
| Rep metric predicate | Three calls in `ed55ec` through `ed57ec`; `a60b88` compares metric type to reps; `7e7884` implements any |
| Rep-center branches | `ed4648` through `ed4f3c`, conditional on goal, template role, and pull predicate |
| Periodized week order | `ed5258` through `ed53e0`; `ed5420` through `ed5434` indexes the selected branch's center list |
| Working sets and RIR | `ed43bc` through `ed4620`, then `ed5760` through `ed57e4` |
| Standard sets and blank performed values | `ed57f0` through `ed5840`; local `TrainingProgram.toWorkout` keeps actuals separate |

The source calendar positions contain zero-based training-day indexes or rest:

| Training days | Seven positions |
| --- | --- |
| 2 | `0, rest, rest, 1, rest, rest, rest` |
| 3 | `0, rest, 1, rest, 2, rest, rest` |
| 4 | `0, rest, 1, rest, 2, 3, rest` |
| 5 | `0, 1, 2, 3, 4, rest, rest` |
| 6 | `0, 1, 2, 3, 4, 5, rest` |

The function preserves selected training-day and exercise order. Working-set counts must be 1–6. Reps use the selected center minus and plus one. RIR uses the source role/pull base and per-set offsets, clamped at zero. All three target fields remain null when the exercise has no rep metric. Periodization changes rep centers, not this stage's RIR offsets.

`cycleCount` remains seven. `cycleUnit=WEEK` records generated-week presentation provenance. This does not establish the source's general Week/Cycle labeling predicate. The existing target model supplies local identities, names for rest days, and unset rest overrides. Those storage details are not recovered source serialization.

## Weekly time allocation

`allocateProgramVolume` returns four category layouts. Each layout records working sets per time-calculation slot and estimated microseconds. A slot is not a selected exercise, a primary or secondary role, or a training day.

| Contract | Source evidence |
| --- | --- |
| Length budgets of 18, 30, 50, 75, 105, and 125 minutes | `edc224` |
| Weekly budget equals length budget × training days | `ecf034` through `ecf048` |
| Upper-body share by resolved split | `ecf04c` through `ecf0f4` |
| Compound share is 0.6 for strength, 0.5 for hypertrophy or both | `ecf134` through `ecf1a8` |
| Compound candidates have at least four upper or two lower total sets | `edb46c` |
| Choose the nearest estimated duration, including over-budget candidates | `edb380` through `edb468` |
| Compound ties choose the last candidate; isolation ties choose the first | `a692d8`, `a693f4`, and call arguments in `ecf134` through `ecf49c` |
| Isolation budget uses the actual remainder after compound allocation | `ecf34c` through `ecf49c` |
| Candidate order and bounds: 31 compound layouts, 86 isolation layouts | `12a0ab0` and `12a0948` |
| Cost adds setup and warm-up per slot, 30 seconds per set, and rest between sets only | `ed31ac`, `ed32ec` |
| Setup is 180 seconds for compounds and 90 for isolation; warm-up is zero or 120 | `ed3330`, `ed33c8` |
| Default rest is 120 seconds for upper compounds, 180 for lower compounds, and 90 for isolation | `a55508`, `a555bc` |

The upper-body share is 0.65 for full body, push/pull, push/pull/legs, push/pull/legs/accessories, and repeated push/pull/legs. It is 0.6 for upper/lower/upper/lower/upper and push/pull/legs/upper/lower. Other resolved splits use 0.5. These shares do not recommend a split.

The source scores duration differences after truncation to whole milliseconds. Explicit zero rest is valid. Isolation still receives a candidate when compound allocation exhausts its region budget. The candidate enumerators are finite, so a larger budget does not create arbitrary extra slots.

## Factual catalog projection

`tooling/import_reference_apk.py --facts-only` preserves nine original fields under `generationFacts`, without source instructions or media. Six fields contain nullable numbers. Their names combine `strength` or `hypertrophy` with `PrimaryCompoundRecommendationLevel`, `SecondaryCompoundRecommendationLevel`, or `AccessoryRecommendationLevel`. The three list fields are `exerciseClassificationStrength`, `exerciseClassificationHypertrophy`, and `exclusionGroupings`; their values remain ordered source IDs. Exclusion groupings remain separate from `exerciseGroup` and are not inferred from exercise names or equipment.

`ExerciseGenerationFacts` distinguishes an absent map key, an explicit null value, and an empty descriptor list. The decoder accepts only the nine named fields and rejects invalid types and nonfinite numbers. A new custom exercise has no inferred generation facts. Explicit facts survive backup encoding.

`DataCodec.catalog(text, bundled)` enriches older imported records by exact ID. Bundled facts fill missing keys only. Imported nulls, empty lists, zero ranks, names, and media remain unchanged. The existing Region and Movement Patterns enrichment keeps the same precedence and does not copy other properties.

The source serializer at `6c9f94` through `6ca0cc` maps import offsets `0x5f`, `0x63`, `0x67`, `0x6b`, `0x6f`, and `0x73` to the six recommendation fields. At `6ca100` and `6ca160`, offsets `0x77` and `0x7b` map to the classification lists. The Exercise constructor at `6d3180` through `6d3274` converts those lists into descriptor fields `0x47` and `0x4b`.

The goal/descriptor accessor at `ed97e4` uses strength recommendations for strength and hypertrophy recommendations for hypertrophy. For both, it uses strength primary-compound recommendations but hypertrophy secondary-compound and accessory recommendations. Descriptor filtering at `edb26c` follows the same goal/role choice. This mapping does not establish the complete ranking policy.

The serializer maps import slot `0x4b` to `exclusionGroupings`. Selector closure `edacf8` counts previous templates whose exercise exclusion IDs intersect the candidate's IDs (`edadac` through `ed9698`). The source has this list on all 1,345 records, including 179 explicit empty lists. Preserving it does not implement that selection policy.

## Unresolved upstream behavior

The retained split routine at `ec7010` through `ecb998` supplies 17 split/frequency variants. Its three category-list fields still lack established selection roles. Those lists are not final exercise selections.

The selector at `eda978` applies movement-pattern, descriptor, and non-null recommendation filters before ranking at `ed8940`. That ranking also considers prior selections, exclusion-group overlap, and equipment overlap. Those contracts are not yet implemented. `ed885c` chooses randomly among the remaining candidates. The complete filtering, ranking, tie policy, and candidate-failure behavior are not recovered.

Weekly category volume is recovered, but its distribution among movement patterns, selected exercises, and training days remains incomplete. Muscle-focus weighting, equipment compatibility, user assessment, and experience effects remain unresolved.

At the inspected wizard call, null goal falls back to strength (`ecec14` through `ecec20`), and null periodization falls back to linear (`ecec30` through `ecec44`). This does not establish every UI No preference representation or split recommendation policy.

## Deload and history boundary

The inspected generated-plan call selects LAST deload when enabled (`ecfc80` through `ecfc94`). Source deload keys FIRST=-1 and LAST=999 are separate from normal keys `0` through `N-1`. The materializer neither appends an eighth normal week nor treats a sentinel as a list index.

The progression unit owns recovered deload transforms. Persisted deload placement, explicit deload overrides, and logger sentinel dispatch require a separate model change. Derived deload defaults must remain separate from explicit overrides so normal-target edits can change them. This materializer produces only normal weeks. It does not adapt loads from workout history.

## Verification boundary

`ProgramMaterializerTest` checks captured press/row target values through codec round-trip and `toWorkout`, five calendar frequencies, contrasting goal/role/pull combinations, all periodization modes, set-count boundaries, and non-rep metrics. It also checks any-match behavior, per-side reps, and absent versus explicit empty facts. Expected fixture values come from native literals and retained capture containers, not the implementation's private tables.

`ProgramVolumeAllocatorTest` checks asymmetric category rest, explicit zero, minimum volumes, over-budget choices, both tie rules, actual-remainder allocation, and finite candidate limits. An excluded temporary test passed 1,836 cases and 7,344 category outputs. Its expected layouts use original ARM64-emitted candidates, not Kotlin private tables, across all 17 supported split/frequency variants, six lengths, three goals, both warm-up settings, and three rest profiles.

An offline Unicorn probe executes the original candidate enumerators, cost routine, and minimum comparator. It supplies VM allocation, yield, collection, and arithmetic shims; it does not run a Dart VM or the source app. The matrix transcribes setup arithmetic separately. Neither check proves full-service or app-level equivalence. Reproduction scripts and outputs remain in the locally excluded `.amp/in/artifacts/smart-generation/` directory.

`ExerciseGenerationFactsTest` checks decoding, backup round-trip, invalid types, and missing-key enrichment without replacing imported nulls or empty lists. The facts-only projection retains all 1,345 exercise IDs in order and every previous public catalog fact. The nine added fields match the retained APK records exactly. The shared private reference pack remains untouched.

These tests do not validate exercise selection under contrasting equipment, focus, duration, or experience. Those upstream contracts are unresolved. No source account or device mutation is part of this recovery.
