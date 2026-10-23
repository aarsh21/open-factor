# Recovered deload contract and progression limits

`Deload.kt` implements the recovered target transform. Programs persist deload placement and explicit target overrides, and program sessions resolve derived targets from the current normal cycle. This does not implement history-based load progression or a complete Smart Progression feature.

## Deload target functions

The retained ARM64 specimen has SHA-256 `870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`. Function addresses below identify that specimen, not a stable application ABI.

| Source function | Recovered behavior |
| --- | --- |
| `0xa4a4d8` | Normal cycle keys are `0..numCycles-1`. First deload adds key `-1` before them. Last deload adds key `999` after them. Deload does not replace a normal cycle. |
| `0xb77be8` | An explicit target for the selected deload takes precedence over a generated target. |
| `0xb77e78` | Without an override, a simple target supplies its target. A periodized target supplies its first or last normal target, according to deload placement. |
| `0xb78088` | If no anchor target exists, the fallback is one blank standard set. This fallback does not receive an extra three RIR. |
| `0xb78148` | Keep the first `floor(max(1, setCount × 0.8))` sets. Taking one item from an empty list still returns an empty list. Set the generated target's skip flag to false. |
| `0xb78408` | Transform each retained set. Convert the `failure` set kind to `standard`, and retain the other set kinds and extra logs. |
| `0xb78530`, `0xb7874c` | Round each present minimum and maximum full-rep target after multiplying by `0.8`. Preserve null bounds. For exercises with a reps metric, set RIR to `(rir ?? 0) + 3`. For other exercises, retain RIR unchanged. |

The serialization body at `0x7a6794` confirms `ExerciseTargetLog` field offsets `0xf = minFullReps`, `0x13 = maxFullReps`, and `0x17 = rir`. The transform retains its rest timer, duration, and distance fields. The local target model represents rep bounds, RIR, set kind, and a common rest override. It does not represent source target duration, distance, or extra logs. Copying `MYO` into a session does not invent component prescriptions or establish source component initialization.

The set-count dispatch resolves to `0x1154dc4`, whose `fcvtms` instruction rounds toward negative infinity. Rep targets use a different operation. Their native call loads Thread offset `0x780`, calls the function, and converts its result with `fcvtzs`.

In Dart 3.13.0, the specimen's `vm_tag_` offset `0x6d8` places the first leaf entry at `0x720`. Leaf index 12 is `LibcRound`, at `0x780`. The runtime binds this entry to C `round(double)`. This establishes rep rounding independently of the captured target tables. The source references are [Thread layout](https://github.com/dart-lang/sdk/blob/3.13.0/runtime/vm/thread.h), [leaf entry order](https://github.com/dart-lang/sdk/blob/3.13.0/runtime/vm/runtime_entry_list.h), [runtime bindings](https://github.com/dart-lang/sdk/blob/3.13.0/runtime/vm/runtime_entry.cc), and [ARM64 leaf calls](https://github.com/dart-lang/sdk/blob/3.13.0/runtime/vm/compiler/assembler/assembler_arm64.cc).

## Independent fixtures

The captured static press target has three sets at 7–9 reps and RIR 3, 2, 1. Its deload has two sets at 6–7 reps and RIR 6, 5. The native arithmetic resolves the captured `6+` display to exactly 6 for this input.

The captured static row target has three sets at 9–11 reps and RIR 2, 1, 0. Its deload has two sets at 7–9 reps and RIR 5, 4.

`DeloadTest` also uses these source-derived boundary fixtures:

- Input set counts `0, 1, 2, 3, 4, 5, 6` produce counts `0, 1, 1, 2, 3, 4, 4`. Six sets distinguish floor from nearest rounding.
- Bounds 3–8 produce 2–6, while 7–9 produce 6–7. These distinguish rep rounding from floor and ceiling.
- Missing RIR becomes 3 only for a reps exercise. A non-reps exercise retains its original RIR, including null.
- RIR 10 becomes 13. The transform does not clamp RIR to a UI display range.
- Different first and last anchors produce different deloads. Explicit overrides bypass generation, including skipped overrides and overrides without a normal anchor.

Set IDs and rest overrides, including zero, remain unchanged in the local transform. They are local model metadata, not fields recovered from the native `ExerciseTarget`. No transform changes its input or writes workout actuals.

## Shared integration boundary

`ProgramDeload.FIRST.sourceCycle` is `-1`, and `LAST.sourceCycle` is `999`. `cycleKeys(normalCount)` returns the source's ordered period keys without changing the normal target list.

`CycleTarget.deloadTarget(isReps)` transforms a target. `List<CycleTarget>.targetForDeload(placement, isReps, overrideTarget)` resolves an override or transforms the adjacent normal target. The caller supplies the recovered reps-metric fact. The helper does not infer this fact from an exercise's name or equipment.

`PlannedExercise.cycles` contains only normal cycles. `ProgramCodec` requires its size to equal `TrainingProgram.cycleCount`. `toWorkout` accepts normal indexes and the configured deload sentinel only. No deload sentinel enters the normal-cycle list or silently maps to a normal period.

The additive schema has nullable `TrainingProgram.deloadPlacement` and separate `PlannedExercise.deloadTargets: Map<ProgramDeload, CycleTarget>`. Old backups omit both fields and retain their previous behavior. Only explicit overrides enter the map. Derived targets are never serialized, so later normal-target edits affect the next derived session.

`PlannedExercise.targetForDeload` resolves explicit overrides before consulting the catalog. Derivation requires an actual `Metrics` property. Missing metadata does not use the logger's legacy Weight/Reps fallback. `startProgramDay` supplies the effective exercise catalog, and unresolved metric facts reject the whole session rather than silently dropping an exercise.

`SetTarget.kind` defaults to `STANDARD` for older data. Unknown kinds reject import. Derived `FAILURE` targets become `STANDARD`, while explicit overrides keep their kinds. Ordinary target RIR remains limited to 0–10. Explicit deload targets permit 0–13, including the recovered result from ordinary RIR 10. Deload override numbers and skip flags use strict JSON types. Empty or skipped overrides remain authoritative, and explicit rest zero is preserved.

## Local program UI

Program Settings offers None, First Cycle, and Last Cycle, or Week labels for weekly plans. The retained chapter 02 reference shows None, First Week, Last Week, and a close control. Applying these choices through the existing local draft Save/Cancel transaction is an adaptation, not a recovered source save policy.

The library displays a separate Deload period before or after normal periods. Start passes `-1` or `999` unchanged. Missing metric facts disable Start with an explanation. The normal target editor's `-1` means all normal cycles only; it is not a deload key.

The exercise's Deload button opens derived or custom targets. Saving without changes stores no override. Editing stores only that placement's override. Use Derived Targets removes it. These routes do not implement calendar scheduling, automatic period advancement, or a history-based load suggestion.

## History-based progression remains unresolved

The native metadata identifies an analysis pipeline, not a fixed load increment:

| Function | Typed contract or observed dependency |
| --- | --- |
| `StrengthService.getProgressionSet`, `0xa5e328` | Accepts a `SimpleSet` and nullable `FixableAutoTarget`. Returns cumulative-above-target, expected-fatigue-per-set, expected-fatiguing-reps, observed-fatigue, progression curve, analyzed set, and nullable target. |
| `getSetAutoProgressionCurve`, `0xa5eeec` | Returns a nullable `XRmCurve`. |
| `getCumulativeFatigue`, `0xa5f234` | Accepts a double and returns a double. The bounded body computes `-2.481 × input^0.582 / 100`. This alone does not establish how history supplies that input. |
| `getExpectedFatiguingReps`, `0xa5f38c` | Accepts a double and an integer. Its body uses the constants `0.582`, `-0.327`, and `1.718213058419244` plus exercise-related state. |
| `getAutoTargetProgressionSet`, `0xa5d160` | Accepts a reps-and-Weight pair, a list of strings, and a Boolean. Returns the progression analysis record. |
| `getAutoTargetTotalRepsAndWeight`, `0xa5d3f0` | Accepts a Boolean and nullable string. Returns an integer-and-Weight pair. |

The analyzed-set record contains `bestCurve`, `expectationDifference`, `oneRm`, `plus5Rm`, `repRatio`, and `set`. `XRmCurve` and `FixableAutoTarget` are separate native types. The bounded progression body includes powers, an exponential, optional previous-curve state, and numeric comparisons. Neither these names nor the captured target tables define a below, within, or above-range load policy.

Further bounded traces recover curve evaluation at `0xa5ad40` as `(A × B^reps + C) × (1 + progress)` and inverse evaluation at `0xa5cfd8` as `log((weight − C) / A) / log(B)`, with null for NaN. The constructor at `0xa5a3a0` sets `C = A × B × 0.25`. The analytics recovery resolves that constant from snapshot double reference 77392. These expressions do not establish the fit inputs or a complete progression policy.

`getAutoTargetTotalRepsAndWeight` enumerates candidate reps and loads and compares weighted distance costs near `0xa5db24`. It is not evidence for a fixed load increment. The complete candidate constraints, tie-breaking, and history-to-target contract remain unresolved.

The following missing contracts block a coherent history-based progression port:

- How eligible logs and prior workouts reach the next-target curve, and how edits, plan changes, set kinds, and dates affect selection. The source filters non-skipped, nonempty logs rather than a local completed flag.
- How `expectationDifference`, `repRatio`, performed reps, and RIR map to below, within, and above-target outcomes, including equality boundaries.
- The complete `XRmCurve` fit, default curve, prior-curve update, and conversion back to a requested rep range and load.
- Conversion of added load, assistance, and bodyweight into `Weight`, including sign, bodyweight fraction, and unit behavior.
- Equipment minimums, maximums, allowed loads, rounding, and tie-breaking when the curve's requested load is unavailable.
- Whether suggested targets are computed on demand or stored, and which source identity links a new session to prior targets after plan edits or restart.

No guessed load increase, plateau rule, rep-range rule, or generic one-rep-max equation is implemented. Next-session load fixtures and progression persistence tests cannot assert source parity until those expected outcomes are recovered.

## Evidence handling and validation

The recovery reads retained metadata and bounded ARM64 disassembly. It does not execute source instructions or mutate a source phone, account, or emulator. The retained REA Dart result remains unsupported. No successful Dart decompilation is claimed.

Private source binaries, indexed chapters 02, 06, and 08, contact sheets, and disassembly remain unpublished. Current local traces and build logs are excluded under `.amp/in/artifacts/progression/`. Earlier transform traces remain under the recovery worktree's `.amp/in/artifacts/progression-recovery/`.

The combined validation copy contains these changes and the coordinated logger `SetKind` dependency. Under the shared Gradle lock, `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` passes, including 93 JVM tests with no failures or skips. Tests cover asymmetric endpoints, current-anchor edits, explicit precedence, wrong sentinels, missing metrics, old backups, RIR 13, kinds, skipped and empty overrides, rest zero, and unrelated data preservation.

All six `TrainingFlowTest` native tests pass on the worker validation APK, including `deloadPlacementAndOverridesSurviveStoreRestartAndStartThroughViewModel`. That test verifies FIRST-derived and LAST-explicit sessions, real store restart, unchanged unrelated history and body data, and backup round-trip.

The synthetic native UI flow passes ordinary, None, FIRST, LAST, no-edit Save, custom edit, reset, Cancel, system Back, process restart, missing Metrics, and 1.3× font cases. Inspected renders show the expected period order, two FIRST sets at 6–7 reps with RIR 13/12, four LAST sets at 2–6 with RIR 9/8/7/6, blank actuals, and the missing-metadata explanation. Longer sheet content remains scrollable above fixed actions. Artifacts are local under `.amp/in/artifacts/deload-native/`.

The assembled app hash is `ee00ce70e89f341893153ed254a8119dccf670488fae07a8a48ffe418eebc9cd`. The installed validation copy, signed with the emulator's existing debug key, has hash `f79b63c9ff5f31bcb862fa1565e6a80090224c08c78cc6181b40340f8e4a5440`. These are worker checks with the logger kind dependency, not parent combined-APK acceptance. Static source traces and local synthetic outcomes are not an executed source-parity comparison.
