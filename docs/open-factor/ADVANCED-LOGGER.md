# Advanced logger contract

This is a bounded implementation, not full logger parity. The source is the
user-supplied Workouts APK. Its ARM64 `libapp.so` SHA-256 is
`870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`.
The addresses below identify static traces, not observed execution. Private
disassembly and native proof stay in the locally excluded `.amp/in/` directory.

## Recovered behavior

- The session-log serializer at `0x7bad98` binds field `+0x1f` to `isComplete`
  at `0x7baf70`. The persisted-log serializer at `0x7ae2a0` binds its own
  `+0x1f` field to `isSkipped`. These are different model types.
- The session predicate at `0x97e104` applies the `0x7297cc` completion accessor
  to component sides. The working-set predicate at `0xf0a518` negates
  completion across the main and additional components.
- The kind transition at `0xf1cacc` retains the main component and starts Myo
  with one empty additional component. The transition at `0xf1cdc8` sets
  Failure RIR to zero. No rep decrement or load division is inferred.
- The single-to-left/right conversion at `0xf0d4e8` copies the original log
  into both sides. Subsequent side actuals and completion are independent.
- The Myo branch at `0xe525cc` uses 15 seconds before the final component,
  then the ordinary rest duration. Its caller at `0xf1dc84` tests for no
  additional components or the final additional-component index.
- The side-rest consumer at `0xe52660` clamps a percentage to 0–100 and scales
  the selected duration. It does not use a Boolean rest-between-sides flag.
- Superset selection at `0xf305f0` finds the minimum first-unfinished working
  set ordinal, excludes the current exercise, and picks the next eligible
  exercise in workout order, with wraparound. It does not compare counts of
  completed sets.
- Ordinary paired volume at `0xd7e488` sums each side's weight times reps.
  It does not average side workloads. Source persistent-per-side weight and
  partial-rep consumers have different arithmetic and are not implemented here.

## Durable representation

Schema version 1 remains readable. Absent `kind` means `STANDARD`; absent links
mean one ordinary logical set. Existing warm-up flags and canonical kg, seconds,
and meter values remain unchanged. Unknown kinds and invalid links are rejected.

`LoggedSet` retains actuals and completion for one log. Its additive fields are:

- `kind`: `STANDARD`, `MYO`, or `FAILURE`.
- `side`: absent, `LEFT`, or `RIGHT`.
- `pairId`: present only on a right log, referring directly to its left log.
- `segmentOf`: present only on an additional Myo component, referring directly
  to the main unsplit or left log. Both sides of an extra share this root.

`SessionExercise.supersetId` groups workout entries. `logicalSets()` exposes
roots, ordered components, and each component's side logs. Navigation completion
requires all logs. Saved-set counts use any completed log in the logical set,
preserving the existing local finish policy without counting sides as extra sets.
Volume still sums performed workloads. Backup and raw exercise exports retain
every side, component, actual, ID, and completion flag.

Import validation rejects duplicate IDs, missing or cross-exercise references,
cycles, right-to-right pairs, duplicate right sides, mismatched kinds, and extras
attached to another extra. Removing a root removes its components and pairs.
Removing an extra removes both sides of that component. Reordering exercises
does not rewrite IDs. Leaving or removing a superset member clears singleton
membership.

## Explicit local policies

- Superset auto-next defaults to enabled. Side rest defaults to 100 percent.
  These compatibility defaults do not change ordinary unsplit sessions and
  are not claims about source installation defaults.
- The reverse side conversion asks to keep left logs and discard right logs.
  It does not silently average independent values. The source reverse-conversion
  choice remains unverified.
- A type change that removes Myo components asks for confirmation. It preserves
  main actuals and IDs. New components start empty, not with guessed rep targets.
- Targets edit the active session only. Main targets apply to both main sides,
  not to unrelated exercises, saved programs, or additional Myo components.
- Health export creates one synthetic segment per eligible logical set and sums
  completed-log repetitions within that set. The segment's 10-second duration
  follows the existing recovered mapper. Grouping and rep summation are local
  adaptations, not recovered source side or partial-rep equivalence.

## Open boundaries

The source finish translation from session completion to persisted skipped logs
is not yet established. Source skipped transitions, partial reps, persistent
per-side metric arithmetic, Myo load propagation, exact side-order preferences,
and source preference defaults remain outside this implementation. The custom
keypad and automatic scrolling are unchanged. No Health provider operation,
permission grant, source-phone mutation, or private-asset publication is part of
this work.

## Checks

`AdvancedLoggerTest` covers asymmetric sides, Myo rest boundaries, independent
auto-next controls, earliest-unfinished superset ordering, root deletion,
component deletion, kind conversion, malformed links, and schema-1 compatibility.
`WorkoutDataTest` retains metric, warm-up, target, and lifecycle coverage.
Health and scoped-export tests check logical identities without discarding raw
side records. Native verification results are recorded separately from these
static and unit-test claims.
