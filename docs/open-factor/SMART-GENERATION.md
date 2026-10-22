# Selected-template program materialization

`materializeProgram` converts already selected exercise templates into a seven-day, seven-normal-week `TrainingProgram`. It does not implement the complete source smart generator. No smart-generation UI calls this function until exercise selection and allocation are recovered.

## Input boundary

The function requires a resolved `ProgramGoal`, a resolved `ProgramPeriodization`, and two through six ordered `SelectedProgramDay` values. Each selected exercise supplies an `Exercise`, allocated working-set count, and template role. Names, color, and gym identity are caller inputs. The function does not resolve a split preference or choose exercises.

The native service uses the record `({MuscleGroup? accessoryMuscle, Exercise exercise, int numSets, ExerciseTemplateType type})` after selection. The materializer takes only the facts that its target-construction stage reads. The lifecycle unit projects factual Movement Patterns into the catalog. `Exercise.movementPatterns` distinguishes absent facts from an explicit `None`. The materializer rejects absent Movement Patterns or Metrics instead of inferring them. It uses exact `Horizontal Pull` and `Vertical Pull` values and the existing `loggingMetrics().any { it.isReps }` classification, including per-side reps.

The public catalog still lacks source generator descriptors and ranking fields. Exercise names, muscles, joint actions, and `searchBoost` are not substitutes.

Equipment, focus, session duration, and experience are not accepted as inputs that the function would silently ignore. Their effect on selection and allocation remains unresolved. The function accepts no nullable preference that could imply a fabricated recommendation policy.

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

## Unresolved upstream behavior

The retained split routine at `ec7010` through `ecb998` supplies 17 split/frequency variants. Its three category-list fields still lack established selection roles. Those lists are not final exercise selections.

The selector at `eda978` applies filters and ranking. The goal/descriptor branch at `ed97e4` reads distinct exercise fields at tagged offsets `0x5f` through `0x73`. The public catalog omits those fields. `ed885c` chooses randomly among the remaining candidates. The complete filtering, ranking, tie policy, and candidate-failure behavior are not recovered.

`edc224` converts WorkoutLength values to duration budgets of 18, 30, 50, 75, 105, and 125 minutes. This does not establish time allocation or set counts. Muscle-focus weighting, equipment compatibility, user assessment, and experience effects remain unresolved.

At the inspected wizard call, null goal falls back to strength (`ecec14` through `ecec20`), and null periodization falls back to linear (`ecec30` through `ecec44`). This does not establish every UI No preference representation or split recommendation policy.

## Deload and history boundary

The inspected generated-plan call selects LAST deload when enabled (`ecfc80` through `ecfc94`). Source deload keys FIRST=-1 and LAST=999 are separate from normal keys `0` through `N-1`. The materializer neither appends an eighth normal week nor treats a sentinel as a list index.

The progression unit owns recovered deload transforms. Persisted deload placement, explicit deload overrides, and logger sentinel dispatch require a separate model change. Derived deload defaults must remain separate from explicit overrides so normal-target edits can change them. This materializer produces only normal weeks. It does not adapt loads from workout history.

## Verification boundary

`ProgramMaterializerTest` checks captured press/row target values through codec round-trip and `toWorkout`, five calendar frequencies, contrasting goal/role/pull combinations, all periodization modes, set-count boundaries, and non-rep metrics. It also checks any-match behavior, per-side reps, and absent versus explicit empty facts. Expected fixture values come from native literals and retained capture containers, not the implementation's private tables.

These tests do not validate selection under contrasting equipment, focus, duration, or experience. Those upstream contracts are unresolved. No source account or device mutation is part of this recovery.
