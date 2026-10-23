# Derived analytics recovery

Status: partial binary recovery, not populated source-app acceptance. Weight
Trend, record detection and the history-dependent strength profile remain
unimplemented. Keep raw readings and saved actuals separate from these derived
values. Do not replace the missing calculations with Epley, a generic moving
average, or a highest-weight record rule.

## Specimen and method

The reference is MacroFactor Workouts 1.4.1, build 130, from the supplied
`Workouts.apk`. The inspected ARM64 `libapp.so` has SHA-256
`870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`.
Addresses below belong only to that binary.

Private evidence is retained in the analytics worktree under
`.amp/in/artifacts/derived-analytics/`. `inspect.py` annotates disassembly with
the existing snapshot metadata. `read_doubles.py` decodes canonical Double
objects from the snapshot; `doubles.json` retains the results. No shared
metadata, source phone, source account, health store or reference pack changed.

`probe_aot.py` executes the original ARM64 bytes with Unicorn and synthetic VM
objects. It does not substitute a Kotlin or Python implementation for the
tested helpers, and does not stub their callees. Run it from the worktree root:

```sh
uv run --with unicorn python .amp/in/artifacts/derived-analytics/probe_aot.py
```

The retained `aot-results.json` records 12 passing scalar cases and the checked
ELF hash. These checks establish the tested helper results, not complete Dart
runtime behavior or chart acceptance. Other findings below are static traces.
No Gradle build or native UI acceptance is implied by this recovery evidence.

## Persisted eligibility is not session completion

`log-json.txt` binds the persisted WorkoutLog serializer at `0x7ae2a0`:

| Object offset | Persisted field |
| --- | --- |
| +7 | fullReps |
| +b | partialReps |
| +f | weight |
| +13 | RIR |
| +17 | seconds |
| +1b | distance |
| +1f | isSkipped |
| +23 | restTimer |

`set-selection.txt` traces `0x7bd284`. A qualifying set is not a warm-up and has
at least one primary or extra component with at least one side that is both
unskipped and nonempty. `0x7bd45c` tests the individual log. `set-empty.txt`
traces `0x7bd4ac`; actual repetitions, partial repetitions, weight, time and
distance determine emptiness. RIR alone does not make a log nonempty.

`set-components.txt` traces primary plus extra logs at `0x7ae704`.
`laterality-logs.txt` traces single versus left/right maps at `0x7ae950`.
Neither extra components nor sides are independent logical set identities.

Session logs have a different contract. `session-component-predicate.txt`
traces reference 114312 at `0x97e104` through reference 114314 at `0x7297cc`.
The latter reads session field +1f. The logger owner's separate serializer
trace, `.amp/in/source/session-log-json.txt` in the advanced-logger worktree,
binds this to **isComplete**, not isSkipped. At `0x7baf70`, serializer
`0x7bad98` writes the isComplete key from +1f; +23 is isModified and +27 is
hasUserTouched.

The session-to-persisted conversion remains unresolved. Open Factor's
`completed` flag must not be treated as a recovered persisted `!isSkipped`
flag. Counting a logical set with any completed actual is an explicit local
eligibility policy until that conversion is recovered.

The scalar execution cases distinguish these boundaries:

| Input | Helper result |
| --- | --- |
| 13 full, 2 partial | Effective repetitions = 13.666666666666666 |
| 5 partial, no full | Effective repetitions = 1.6666666666666667 |
| No repetitions | Effective repetitions = 0 |
| Skipped, 19 full, 7 partial | Effective repetitions = 0 |
| RIR 3 only | Empty |
| Zero full and partial | Empty |
| One partial only | Nonempty |
| Skipped with 9 full | Nonempty, despite being skipped |
| 11 full, not skipped | Eligible log |
| 11 full, skipped | Ineligible log |
| Empty, not skipped | Ineligible log |
| RIR 2 only | Ineligible log |

## Volume and strength use different side arithmetic

`volume-aggregate.txt` traces `0xd7df64`. For paired persisted logs, the ordinary
branch at `0xd7e488..0xd7e664` sums left and right workload:

\[
V = w_L r_L + w_R r_R
\]

The weightPerSidePersistent branch at `0xd7e2c8..0xd7e484` instead calculates:

\[
V = (w_L + w_R)(r_L + r_R)
\]

Here the effective repetitions helper at `0xd7f9e0` returns zero for a skipped
log, otherwise full repetitions plus one third of partial repetitions.
The weight helper at `0xa60800` returns zero for a skipped log, otherwise the
logged Weight plus its supplied extra. An explicit override bypasses that
helper. The caller's bodyweight/assistance policy is not yet fully bound.

An asymmetric arithmetic control separates the paired branches. Left weight
17 with 7 full and 2 partial repetitions, and right weight 29 with 4 full and
1 partial repetition, gives ordinary volume 256, but persistent-per-side volume
552. These are consequences of the traced expressions, not executed populated
source-app results.

The single-log branch contains metric-dependent factors of 2 and 0.5.
`0xa613d4` tests membership in weightPerSide, weightPerSidePersistent,
repsPerSide, durationPerSide and distanceShortPerSide. A universal rule to sum,
halve or average all sides would be wrong. Group logical set counts, but do
not discard distinct performed workload as duplicate data.

Strength uses a separate side arithmetic mean at `0xa57d54`, retained in
`arithmetic-mean.txt` and `side-aggregation.txt`. Its full-repetition mapper
at `0xa5811c` excludes skipped logs and does not use the volume helper's
partial-repetition weighting. Its RIR mapper at `0xa58068` treats missing RIR
as zero. Do not reuse a volume-normalized repetition count for strength.

## Strength profile and curve

`strength-entry.txt` traces history folding at `0xa599fc` and closure
`0xa59af8`. The analysis result contains bestCurve, expectationDifference,
oneRm, plus5Rm, repRatio and set. `simple-workouts.txt` and `simple-sets.txt`
trace workout selection at `0xa60098`, eligible sets at `0xa60338` and the
mapper at `0xa604b0`. This is a history-dependent analysis, not a standalone
repetitions-to-1-RM formula.

`body-contribution.txt` traces `0xa5f7b8`. Missing bodyweight returns null. A
positive exercise contribution coefficient multiplies bodyweight; otherwise
the contribution is zero. This does not establish the dated-weight fallback
or the assistance/load policy of every caller.

`strength-curve.txt` identifies `0xa5a3a0` as XRmCurve construction, not
evaluation. The constructor stores B at +7, Weight A at +f, and
\(C = 0.25AB\) at +13. Snapshot Double reference 77392 is exactly 0.25.
`weight-multiply.txt` binds `0x5df2a8` through `0x1156418` to multiplication;
`number-add.txt` binds `0x11566a8` to addition.

The progression owner's `curve-evaluate.asm`, under their private
`.amp/in/artifacts/progression/`, traces evaluation at `0xa5ad40..0xa5af3c`:

\[
W(r,p) = (AB^r + C)(1+p)
\]

Their `target-context.asm` traces the inverse at `0xa5cfd8..0xa5d0f4` as
\(\log((W-C)/A)/\log(B)\), with null on NaN. Target selection enumerates
candidates and compares costs; it is not a fixed load increment.

`curve-regression.txt` traces transformed regression at `0xa5a470`, including
sumX, sumXX, sumXY and sumY. Full constructor inputs, curve selection, history
folding, fatigue updates and caller bounds remain unresolved. These isolated
expressions are insufficient to implement the displayed estimated 1-RM.

## Weight Trend

`trend-core.txt` traces the V1 entry at `0x78221c`. It first calls initialization
at `0x782e34`, using up to eight entries. The later recurrence uses alpha
0.22222 for ema9 and 0.1 otherwise. Initialization regression/clamping and
daily-gap rules remain unresolved. A recurrence alone is not the algorithm.

V2 at `0x774ed8`, including a horizon smoother near `0x77d250`, is a distinct
computation. `trend-provider.txt` traces `0x11c4308`: the provider reads scale
entries and the user's version, defaults to V1 when absent, and reads goals
and prediction style for V2. Both paths route through `0x11c456c`. Full
configuration lifecycle and caller behavior remain unresolved. Do not apply
one arbitrary exponential average to all users.

## Dates and records

`dated-weight-lookup.txt` traces `0xa5f9a4`, which constructs the next calendar
day and searches keys. `lookup-bound.txt` and `lookup-fallback.txt` retain the
search and first-or-null fallback at `0x5ddbf0`. Ordering and fallback policy
remain unresolved. This evidence does not establish a prior-only lookup.
`0xa60938` negates DateTime.isAfter at `0x1076f54`, making that helper's optional
upper cutoff inclusive. It does not establish every dashboard date window.

`recent-records.txt` and `record-metrics.txt` recover volume, repetitions and
oneRepMax record choices, with oneRepMax as the default. Record detection,
ranking, ties, baselines and caller inclusion rules remain unresolved.

## Implementation boundary

Dashboard and Levels now count each logical working set once when it contains
any completed actual. A completed right side or extra component counts even
when its root log is unfinished. Warm-ups do not count. This preserves the
ordinary completed-set policy, including legacy completed rows without actuals;
it does not import the source's nonempty/unskipped predicate. Dashboard exercise
history keeps each completed actual and identifies its set, side and Myo
component. The exercise-library history renderer belongs to the logger change.

The regression fixture uses unequal side weights and repetition counts, paired
Myo components, an incomplete pair, an extra-only completion, a warm-up and an
uncompleted log with actuals. It checks four logical sets and local volume 573
kg-reps, then three sets and 279 kg-reps after undoing the first logical set.
It also checks backup retention, primary/secondary contributions, active/saved
identity deduplication, and ordinary bodyweight, assistance, time and distance
completion. Existing date-window tests remain unchanged. Local volume still
uses recorded external weight; no bodyweight or assistance formula was added.

On the analytics checkout with the logger's two schema commits, all 95 JVM tests
pass, lint reports zero errors and eleven warnings, and the debug APK builds.
The schema dependencies are the logger's original commits
[`787380cc`](https://github.com/aarsh21/open-factor/commit/787380cca31b8aba7c4f122edb4bc70f6eea2b36)
and
[`79db940c`](https://github.com/aarsh21/open-factor/commit/79db940c89b2d9ab8e04e5f919986111303c8ff4).
Do not integrate their validation-only cherry-pick copies a second time.
`build-final.log` and `host-results/` retain this bounded run.

The bounded offline native check passes on emulator-5580 at normal and 1.3 font
scale. Dashboard exercise and muscle tiles report two logical working sets.
Exercise history shows six distinct completed actuals: both root sides, both
Myo sides, a right-only second set and a warm-up. The unfinished left actual is
absent. Both final captures were inspected; every label and value is readable
without clipping or overlap. History data is unchanged after viewing and
restarting at the second font scale. This checks the worker APK, not the
parent's combined application or a populated source-app comparison.

The tested APK has SHA-256
`2d95c2c67ca04b7a79a94b653d3a463be60243671f28c542e250206caa4e8fe5`.
`native-final/result.json`, `canonical-history.json`, `history-normal.png` and
`history-130.png` retain the final evidence. The first driver run passed its UI
assertions but failed a comparison against sparse hand-written JSON. Inspection
of `native/failed-state.tar` and the decoder confirmed added codec defaults;
omitted weight/repetition keys use legacy zero/eight defaults. The corrected
fixture supplies explicit nulls for the unfinished side, validates every
supplied field, and compares the complete canonical history after the flow.
That rerun passes on the unchanged APK. An earlier Python module-name collision
failed before any device access. Neither failure is counted as a passing run.

`native-final/independent-release.json` records the separate restoration check.
The original application/test APKs, all 21 private-tree entries, both original
photo files, modes, UID/GID and device settings match the incoming baseline.
The application is force-stopped and the emulator lease is released. No source
phone, source account or health-store operation ran.

Keep the existing raw six-range body charts and explicit unavailable states
for Weight Trend, records and estimated 1-RM. The recovered helpers narrow the
remaining work but do not justify a partial formula in those screens. Complete
the caller contracts and compare asymmetric populated results before claiming
derived analytics parity.
