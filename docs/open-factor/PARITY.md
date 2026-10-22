# Offline parity acceptance

**Status: not one-to-one and not production-ready.** This is an acceptance
ledger, not a claim that matching labels or passing local tests certify the
source app's behavior. The reference is MacroFactor Workouts 1.4.1 (130).

## Product boundary

Open Factor must work without an account, Internet access, subscription, remote
media, or a service-dependent integrity check. Preserve the Nuvio floating pill
and grouped Settings presentation. The global plus sits inside the middle of the
pill, between Workout and Exercises. It is an action, not a selected destination.
The user's latest media requirements are full-width, filled demonstrations,
1X/2.5X/5X playback options, and no progress or seek bar. These explicit choices
override the earlier lower-speed and separate-plus interpretation.

Offline-only does **not** excuse missing measurements, photos, charts, local
export, accessibility, language support or device-local Health Connect. Those
are distinct capabilities. Required cloud accounts and synchronization are
excluded; unimplemented local functions remain gaps.

## Evidence and interpretation

The [reference tour](REFERENCE-TOUR.md) indexes fourteen retained source chapters.
Private source evidence lives under the prior investigation's
`inspection/private/`; it is not a public application asset. The October 22
continuation also captured the connected source phone's Workout, Shortcuts,
Weight, Full Body, Progress Photos, More and Exercises screens. It did not save
or delete source workouts, programs, body records or photos.

Only this thread operated the phone. It was already dozing and keyguard-locked
at first inspection; no power, sleep or lock command was issued. The reusable
testing scripts reject physical-device serials and run on a disposable emulator.
A separate temporary package was also installed for a bounded physical playback
check, without replacing the differently signed existing Open Factor app. That
run played three camera views but failed its pause assertion. It is not a full
physical compatibility pass.

Use these evidence distinctions:

- **Observed:** a source screen, control or interaction was captured.
- **Local verification:** the installed Open Factor behavior was exercised.
- **Different/missing:** source evidence contradicts or exceeds our implementation.
- **Unobserved:** there is no source result against which to assert equivalence.

An empty chart cannot establish a populated chart's calculation. A draft cannot
establish save/reload behavior. A default value cannot establish an algorithm.

## Cycles are real; the full program policy is not recovered

The current source phone literally displays **Cycle 1**. Retained manual-program
evidence (`source-scratch-with-exercise.xml`, `source-scratch-set-config.xml` and
`program-manual-970.png`) displays **Day / Cycle**, **7 Cycles** and **1 Standard
Set**. Thus the word and captured default were not invented.

Generated weekly plans instead display **Day / Week**, **7 Weeks** and a separate
**Deload**. A cycle is consistent with a repetition of the program's ordered day
sequence, but the precise source rule choosing Week versus Cycle remains
unrecovered. It must not be equated with a seven-day week or a whole training
block in every context.

Our fixed seven-cycle manual editor is a limitation, not a verified source
restriction. Copying minimum reps into a new session is not Smart Progression.
The recovered periodization tables do not recover the complete generator,
history-dependent strength profile, calendar placement or deload transformation.
See [manual programs](PROGRAMS.md) and the private `program-recovery/REPORT.md`.

## Acceptance matrix

All local acceptance cases must run without an active default network. "Partial"
below is not an acceptance pass.

| Surface | Evidence-backed state | Remaining acceptance work |
| --- | --- | --- |
| Navigation | Nuvio shell retained; plus inside its middle with two destinations on each side. All four roots and shortcuts checked at normal and 1.3× text size; full navigation labels remain visible | System Back and IME clearance still need broader coverage. Source shortcut customization, color and Up Next composition remain absent. |
| Shortcuts | History, empty/resume workout, new program and new workout have local routes | Weight, Photos and Metrics destinations and source Up Next card remain missing. Do not add inert buttons to imply completion. |
| Dashboard | Rings, data scope and section visibility exist | Recent Records focus, configurable analytics/muscle/exercise/body tiles, weigh-in habits, Weight Trend, Steps and reset defaults differ or are absent. Source totals/target algorithms are unobserved. |
| Workout/calendar | Local program library and workout history entry points exist | Source calendar expansion/placement, day/program composition, drag/drop library and program controls are not equivalent. |
| Manual programs | Drafts, days, targets, local save/activation and backup exist | Variable count editing, contextual Week/Cycle terminology, icons, circular target controls, exact saving scopes, advanced sets and calendar placement remain open. Source save/activation outcomes were not captured. |
| Smart programs | Wizard inputs and some static tables were recovered | No complete offline generator, progression or deload engine. Never substitute an invented formula and call it source parity. |
| Logger | Local pause, restart, auto-next, completion and history foundation; editable session targets and explicit warm-up insertion added | Source custom keypad, metric-specific/blank initial inputs, bodyweight contribution, advanced set types, supersets, target plan-update flow and exact layout remain different. Empty warm-up insertion does not implement source schemes. |
| Rest | Deadline persists; automatic rest and per-exercise overrides exist | Source last-warm-up, between-exercise and left/right policies and rest sheets are not recovered equivalents. |
| Live settings | Workout and gym settings entry points return to the session | Complete source settings are absent. Showing RIR is not equivalent to source auto-population under RIR Tracking. |
| History/summary | Local completion, repeat, deletion and stored sets exist | Populated source history, summary, record handling, incomplete-set rules and bodyweight-volume calculations are unobserved. Existing local arithmetic is not a source oracle. |
| Exercise library | Bundled catalog, gym/type/laterality filters, grouped browse, name/alias search and favorites exist | Source All/Performed/Deprioritized/Modified/Archived scopes, literal resistance selector, selected-muscle ordering and exact ranking need work. Archived was confirmed by horizontally scrolling the live source tab row; the fifth scope is not Custom. Full filter combinations remain unobserved. |
| Exercise detail | Imported properties, instructions, media and local history exist. Offline emulator checks passed for Chin-Up's three cameras, instruction collapse and expansion, native 1X/2.5X/5X playback, pause and background reset. Rendered full-width center-cropped media has no seek bar | Source modal/contextual Add action and history analytics still differ. Physical-device compatibility is not certified. Source autoplay and camera-switch continuity remain unobserved. |
| Custom exercises | Basic local name/muscle/equipment/instructions form exists | Source staged required metrics, structured muscles/equipment alternatives/support, ROM/stability, final details/review and durable rich metadata are missing. |
| Gyms | Local presets, equipment, weights and gym selection exist | Category bulk selection, default-weight summaries, quantity/enabled states, local unit editor and source presentation differ. Everything/Warehouse/Home counts differ between retained runtime and bundled catalog; resolve snapshot identity before deleting entities. |
| Body data/photos | Source dated Scale Weight, Full Body and Front/Side/Back photo forms observed | No equivalent local record/photo/trend implementation. Full Body inventory observed live includes neck, shoulders, bust, chest, waist, hips and left/right bicep, forearm, wrist, thigh, calf and ankle. Do not derive a trend/visual-fat estimator from captured values. |
| Levels | Front/back map, period selection and contribution foundation exist | Source row order/sheet differ. Populated weighting, averaging, map colors and drilldown rules remain unobserved. |
| Onboarding | Local defer/resume, validation and gym/program handoff exist | Source post-login forms were not captured sufficiently for parity. Onboarding body values are not dated body-data records. Account/login gates are intentionally omitted. |
| Settings | Grouped Nuvio appearance, weight units, rest and backup exist | Energy/height/clock units, language, theme, full workout controls, shortcut configuration, visibility and tutorials/help remain incomplete. |
| Data export | Full Open Factor JSON backup/restore exists | Source quick/granular category/time export is not equivalent. Body/photos/visibility additions must participate in backup, not live in an unexported side store. |
| Integrations | No required remote service | Optional device-local Health Connect is unimplemented; source grant/sync was not executed. |

## Media compression decision

The received originals contain 1,831 HEVC clips (2,731,419,404 bytes). Preserve the
verified immutable export and imported mapping. Three bounded four-second
sections were tested with HEVC CRF 24/28 and AV1 CRF 30; all nine derivatives
decoded and their quality metrics were measured against the already-lossy
originals. These are experiments, not a corpus forecast or camera-master quality
comparison.

Stream-copy remuxing three full samples saved essentially nothing. Smaller
re-encodes are lossy; native-resolution comparisons show some smoothing. No
bulk transcode or replacement of imported originals was performed. "Visually
similar" is not "without quality loss." AV1 also had sample mux-duration
differences and requires a separate Android compatibility decision.

## Reproducible local checks

Build from `openfactor/` using its own wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

After installing the application/test APKs on an explicitly disposable emulator:

```sh
adb -s emulator-5580 shell am instrument -w \
  org.openfactor.app.test/androidx.test.runner.AndroidJUnitRunner
python tooling/android_offline_suite.py --serial emulator-5580 \
  --output .amp/in/artifacts/parity/offline-suite
```

The offline suite records installed APK identity and package permissions, disables
Wi-Fi/mobile data, checks no active default network before each flow, and restores
prior network settings in `finally`. It runs workout/shortcut/targets/warm-up/live
settings, original-media cameras/lifecycle, manual programs and onboarding flows.
The package test verifies absence of Internet permission. Local checks still do
not certify the missing/unobserved rows above. Record actual run outcomes in the
review artifacts, including failures; do not mark this entire matrix accepted.
