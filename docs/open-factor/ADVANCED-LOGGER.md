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

### Native verification, 23 October 2026

The logger candidate built from
[`088d6190`](https://github.com/aarsh21/open-factor/commit/088d6190)
passed 96 unit tests and built both debug APKs. The guarded build used Gradle
slot 1, JDK 17, one worker, a 1536 MB heap, and in-process Kotlin compilation.
These results do not certify the parent's combined candidate.

The first in-place install failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
The checkout's default debug certificate differed from the installed baseline.
Separate copies of both APKs were signed with the baseline debug certificate,
whose SHA-256 is
`374abaf9957274f11948a04eff2e0a6e41c85eceaae80900e6310b09b87dc400`.
The original build outputs were not changed. The signed copies installed with
`-r`, without uninstalling or clearing application data.

The tested artifacts have these SHA-256 hashes:

| Artifact | SHA-256 |
| --- | --- |
| Assembled app | `6d0a0959f1cbdb38b7075eea3e7a6277806207f69b82a121a8dcd184f78dff54` |
| Assembled test APK | `8f9aa1503e26855105fc88518fc6b2990099a163ed8798bb9048c0660f4d9f27` |
| Installed signed app | `2e977c1de3d7917593932943dc71cee1d9ce29ac9dbe119405e63c5ff31750bc` |
| Installed signed test APK | `1c9ede1e3ddcd18ba442b33347d53e717f45590199a1c9adc77d95817d9e9d2a` |

On exclusively leased emulator 5582, `AdvancedLoggerStorageTest` passed its
real-store reload, backup, reorder, and finish checks. The UI flow also passed:

- Standard changed to Failure with RIR zero, then to Myo with an empty extra.
- Splitting copied main actuals. Editing the right side left the left unchanged.
- Superset navigation waited for both sides of the main and extra components.
- A cold restart preserved the active session. Finish retained two logical sets,
  five actual logs, and 431 kg-reps: 20 × 7 + 19 × 5 + 20 × 3 + 20 × 2 + 16 × 6.
- The superset toggle changed independently. A 50 percent side-rest setting
  survived a cold restart.
- Screenshot inspection found no overlap or clipping in the default logger,
  Failure row, Myo side rows, superset dialog, rest dialog, or saved history.
  Myo side rows, rest controls, and history also passed inspection at font scale 1.3.

These checks exercise local behavior. They do not establish the source's
completion-to-skipped translation or resolve the other open boundaries above.

Before installation and after restoration, independent checks matched both
baseline APK hashes, all 22 private entries including 12 photos, and the recorded
settings. Private checks compared file bytes, membership, types, modes, UID, and
GID. Cache directories retained UID 10209, GID 20209, and mode 2771. The app was
force-stopped and the running AVD released without provider operations.

Private evidence is under `.amp/in/artifacts/native/`: `instrumentation.log`,
`result.json`, `followup-result.json`, screenshot and hierarchy captures,
`baseline.json`, and `restoration.json`. APKs, archives, and private data are not
published with this document.
