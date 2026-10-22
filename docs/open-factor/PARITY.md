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
1X/2.5X/5X playback options, autoplay on opening or changing cameras, and no
progress or seek bar. Explicit pause and background pause remain available.
These choices override the earlier lower-speed and separate-plus interpretation.

Offline-only does **not** excuse missing measurements, photos, charts, local
export, accessibility, language support or device-local Health Connect. Those
are distinct capabilities. Required cloud accounts and synchronization are
excluded; unimplemented local functions remain gaps.

## Evidence and interpretation

The [reference tour](REFERENCE-TOUR.md) indexes fourteen retained source chapters.
The [whole-app audit](reviews/whole-app-parity-2026-10-22.md) records the committed
baseline and fifteen functional and UX acceptance units. Its body-data and
autoplay findings describe that baseline, not acceptance of subsequent work.
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
restriction. Program sessions keep performed values blank, separate from
prescriptions. Static prescriptions are not Smart Progression.
The recovered periodization tables do not recover the complete generator,
history-dependent strength profile, calendar placement or deload transformation.
See [manual programs](PROGRAMS.md) and the private `program-recovery/REPORT.md`.

## Acceptance matrix

All local acceptance cases must run without an active default network. "Partial"
below is not an acceptance pass.

| Surface | Evidence-backed state | Remaining acceptance work |
| --- | --- | --- |
| Navigation | Nuvio shell retained; plus inside its middle with two destinations on each side. All four roots and shortcuts checked at normal and 1.3× text size; full navigation labels remain visible | System Back and IME clearance still need broader coverage. Source shortcut customization, color and Up Next composition remain absent. |
| Shortcuts | History, empty/resume workout, new program, new workout, Log Weight, Log Measurements and Log Photos have local routes | The source Up Next card remains missing. Shortcut layout and customization are not source-equivalent. |
| Dashboard | Rings, data scope and section visibility exist. A local Scale Weight row opens body history and shows the latest dated reading in the selected weight unit | Recent Records focus, configurable analytics/muscle/exercise/body tiles, weigh-in habits, Weight Trend, Steps and reset defaults differ or are absent. Source totals/target algorithms are unobserved. |
| Workout/calendar | Empty Workout now opens a persisted pre-start composition with name, gym, target-muscle summary, exercises and ordering; only Start Workout creates the timed session. Offline restart and draft-to-session checks passed without changing body records | Source calendar expansion/placement, full day/program composition, drag/drop library and program controls are not equivalent. A nonempty source time-estimate policy is unrecovered. |
| Manual programs | Drafts, days, targets, local save/activation and backup exist | Variable count editing, contextual Week/Cycle terminology, icons, circular target controls, exact saving scopes, advanced sets and calendar placement remain open. Source save/activation outcomes were not captured. |
| Smart programs | Wizard inputs and some static tables were recovered | No complete offline generator, progression or deload engine. Never substitute an invented formula and call it source parity. |
| Logger | Local default is one blank metric-specific row. Required actuals, optional rep-based RIR, assistance, seconds and meters persist through restart, finish, history and backup. All 18 catalog metric combinations passed the offline UI flow. The bodyweight badge uses a dated prior weight and the catalog contribution coefficient. Notes use Save/Cancel dialogs; Finish is in Workout options. Converted loads remain readable beside RIR at normal and 1.3× text size | Source custom keypad, independent side sequencing, persistent-per-side behavior, advanced set types, supersets, target plan-update flow and exact layout remain different or unaccepted. Per-side labels do not establish side sequencing. The contribution badge does not establish source volume/progression arithmetic. Empty warm-up insertion does not implement source schemes. |
| Rest | Deadline persists; automatic rest and per-exercise defaults and entry overrides exist. Fresh entries copy the default; explicit program rest wins, including zero. Existing drafts, sessions and history keep their saved timers | Source last-warm-up, between-exercise and left/right policies and rest sheets are not recovered equivalents. |
| Live settings | Workout and gym settings entry points return to the session | Complete source settings are absent. Showing RIR is not equivalent to source auto-population under RIR Tracking. |
| History/summary | Local completion, repeat, deletion and stored sets exist | Populated source history, summary, record handling, incomplete-set rules and bodyweight-volume calculations are unobserved. Existing local arithmetic is not a source oracle. |
| Exercise library | Bundled catalog, gym/type/laterality filters, grouped browse, name/alias search and favorites exist. Changed queries/filters reset list position; unchanged-query detail return preserves it. The searchable Resistance sheet filters actual equipment entities across resistance alternatives, excluding support-only equipment. All, Performed and Deprioritized have local membership rules. See the [library review](reviews/library-search-01.md) and [exercise-settings review](reviews/exercise-settings-01.md) | Modified/Archived, source scope membership, selected-muscle ordering and exact ranking need work. Scope controls still differ from the observed tab row. The reproduced hidden-first-result defect is fixed locally. Single-entity selection and immediate dismissal are local policies; source selection combinations and persistence remain unobserved. Archived was confirmed by horizontally scrolling the live source tab row; the fifth scope is not Custom. |
| Exercise detail | Root-library selection opens exercise settings, whose Info row opens a closeable detail sheet without Add. Contextual detail Add preserves draft/program/live-session context; offline program-picker checks confirmed it changes the intended program day without changing an active session. Imported properties, instructions, media and local history exist. Combined offline emulator checks in the new Info sheet passed for Chin-Up autoplay on opening, all three cameras and reopening, instruction collapse and expansion, native 1X/2.5X/5X playback, explicit pause and background reset. Sheet dismissal and settings Back preserve the library query. Clip loading resets for each selected clip instead of temporarily reopening the previous camera. Rendered full-width center-cropped media has no seek bar. Empty-cache fault injection first reproduced a blank unprepared player; all three camera caches rebuild byte-for-byte from bundled originals and autoplay offline | Exact source sheet geometry and history analytics still differ. Physical-device compatibility is not certified. Source autoplay and camera-switch continuity remain unobserved. Nonempty corrupt-cache, decoder and storage failures remain unaccepted. |
| Exercise settings | Durable notes, rest defaults and Do Not Recommend participate in backup. Fresh draft/live/direct/program entries copy defaults without rewriting existing records. Logger notes are visible and editable per entry. Edit Duplicate opens an unsaved five-step copy; Create assigns a fresh identity without changing the original or inheriting its media, history or defaults. Performed uses completed-set history; Deprioritized uses Do Not Recommend. The UI states that no generator is available | Source Save, duplicate outcomes and scope policies are unobserved. Equipment-weight overrides and left/right rest remain open. Do Not Recommend does not implement a recommendation engine. See the [local defaults contract](reviews/exercise-settings-01.md) and [duplicate policy](reviews/exercise-duplicate-01.md). |
| Custom exercises | Five-step creation supports explicit metrics, primary/secondary muscles, resistance/support alternatives, ROM/stability, contribution, aliases and review. Wizard edits remain separate until Create. Rich metadata participates in backup and gym availability; caller context is preserved. Recovered Region facts prefill known catalog types, including older private packs without rewriting them. The unsupported alias cap is removed: all eight aliases and 515 characters survive Review, Create and restart unchanged. Info combines recognized Type/Region facts into the six editor labels; unresolved records retain raw facts. Copy headings remain readable at normal and enlarged text. See the [custom-exercise review](reviews/custom-exercises-01.md), [Region repair](reviews/catalog-region-01.md), [alias regression](reviews/exercise-alias-preservation-01.md), [type presentation](reviews/exercise-type-presentation-01.md) and [heading overflow](reviews/exercise-heading-overflow-01.md) | Source Create was not executed. Muscle-tap cycling, metric-family restrictions and complete choice menus are local policies or unobserved. Seventeen unresolved catalog-copy classifications, Modified/Archived, remaining lifecycle actions, independent side logging and exact source controls remain unaccepted. |
| Gyms | Local presets, equipment, weights and gym selection exist | Category bulk selection, default-weight summaries, quantity/enabled states, local unit editor and source presentation differ. Everything/Warehouse/Home counts differ between retained runtime and bundled catalog; resolve snapshot identity before deleting entities. |
| Body data/photos | Local dated scale-weight records support kg/lb input, unit-aware history, optional manual scale fat, editing and confirmed deletion. The dated Full Body sheet supports the 18 observed circumference fields and a separate manual Visual Body Fat input/reference. Public builds show reference labels with an explicit missing-art disclosure; a private opt-in build can use the nine original SVGs. See [Full Body policy](BODY-MEASUREMENTS.md). The compact dated photo sheet has square Front/Side/Back slots, local attachment, private original-byte copies, uncropped full-view reopen, date/slot edits, discard, confirmed deletion and complete JSON backup/restore. Quick photo and measurement sheets retain their real caller underneath and return there on dismissal; history edits return to Body Data. Save-to-history is a local adaptation. Raw charts expose weight, scale fat, visual fat and all 18 circumferences with six ranges and dated selection. Their [calendar-window policy](BODY-CHARTS.md) is local. Body UI/SAF checks preserved training and private photo bytes | Weight Trend smoothing, source aggregation, initialization/estimation and reference-card conversion are unrecovered. Exact source spacing and populated history are unaccepted; reference art is absent from public builds and source photo pose art is not bundled. Full Body outside-tap dismissal is unverified where the status/cutout region covers its scrim. Source save, same-day record policy, photo outcomes and populated trend calculations are unobserved. No smoothing or visual-fat algorithm is inferred. |
| Levels | Front/back map, period selection and contribution foundation exist | Source row order/sheet differ. Populated weighting, averaging, map colors and drilldown rules remain unobserved. |
| Onboarding | Local defer/resume, validation and gym/program handoff exist | Source post-login forms were not captured sufficiently for parity. Onboarding body values are not dated body-data records. Account/login gates are intentionally omitted. |
| Settings | Grouped Nuvio appearance, weight units, rest and backup exist | Energy/height/clock units, language, theme, full workout controls, shortcut configuration, visibility and tutorials/help remain incomplete. |
| Data export | Full Open Factor JSON backup/restore includes rich custom exercises, exercise defaults, scale weights, body measurements and private original photo assets. Photo export streams bytes; restore stages and validates attachments before confirmation. Older backups remain readable; invalid records or missing/corrupt attachments reject before saved data changes | Source quick/granular category/time export is not equivalent. Future visibility additions must participate in backup. |
| Integrations | No required remote service | Optional device-local Health Connect is unimplemented; source grant/sync was not executed. |

## Local body-data policy

Retained `source-log-weight.xml` establishes a date, weight and optional body fat.
`source-body-metrics.xml` and the lower Full Body captures establish neck,
shoulders, bust, chest, waist, hips, and separate left/right bicep, forearm, wrist,
thigh, calf and ankle fields. The source forms show cm. Private captures remain
outside Git and are not bundled as application assets.

Local records use stable IDs and ISO calendar dates, with kg and cm as storage
units. Multiple records on the same date coexist; editing replaces only the
selected ID. This is a local policy, not a recovered source rule. History sorts
by date, and onboarding profile values do not create dated records. Weight input
can switch kg/lb independently; history follows Settings → Units. Display rounds
to two decimals, while an unchanged edit preserves the stored value.

The local input limits are 0 < weight ≤ 5000 kg, 0–100% body fat, and
0 < circumference ≤ 1000 cm. Full Body requires a visual-fat percentage or at
least one circumference; visual fat is independent of scale fat. These
validation limits are not source restrictions or medical guidance. Full restore
replaces all app data, so an older backup without body records removes current
body records after confirmation. Export a current backup before restoring one.

The [progress-photo policy](PROGRESS-PHOTOS.md) documents private original-byte
storage, shared-image cleanup, staged restore, limits and source-evidence gaps.
Combined parent checks passed with both workout composition and photos present:
40 JVM tests, five Android tests and the complete offline photo flow. Cases cover
attachment, editing, restart, JSON export and restore. The complete workout and
media regression also passed with photos present, preserving body records and
private photo bytes. These checks do not accept every parity unit.

The metric foundation adds three JVM tests, for 43 passing tests, and retains
five passing Android tests. Its complete UI flow covers all 18 catalog metric
combinations, invalid and cleared inputs, dated bodyweight, optional RIR,
unit changes, notes, restart and history. A subsequent width-only fix preserves
the full displayed load at both tested font sizes.

The later workout/media and manual-program flows passed on that same APK.
Onboarding initially failed because its search result was above a retained
scroll offset. The corrected harness scrolls its fixture to the top; the
onboarding rerun passed on the same installed APK. Body entry, enlarged type,
deletion and native document export/restore also passed with existing photos.
That run preserved the photo records and original bytes, then restored the
prior synthetic app fixture byte-for-byte.

Artifacts retain failed harness attempts, including binary/ADB fixture transport,
an assumed kg preference and the offscreen search result. No uninterrupted
aggregate-suite pass is asserted. These local passes do not close the source
parity gaps above.

The chart/custom/library baseline passes build, lint and 47 JVM tests. Two codec
regressions failed before the correction: preserving the catalog's empty-muscle
fallback without assigning it to custom exercises, and rejecting custom body
contributions above the form's 100% limit during restore. Library acceptance
separately reproduces the old hidden-first-result failure, then checks changed
queries, unchanged-query detail return, resistance alternatives, empty/clear,
process recreation and unchanged persisted data. This is not source-ranking
acceptance. The temporary onboarding scroll workaround is no longer needed.

The parent chart run initially stopped after enlarged-text recreation because
its fast lookup swipe flung past the entire Body Data title. A focused trace
reproduced the skipped title from the top of Settings. A shorter, slower lookup
drag passes that same restart/navigation case at 1.3× font; the failed run and
trace are retained rather than reported as an aggregate pass.

The subsequent parent chart/custom/library sequence passed on one combined
installed APK, followed by exact restoration of the original synthetic fixture
and photo bytes. It includes all six chart ranges, zero versus missing values,
point/row selection, kg/lb/cm, enlarged text and restart; five-step custom
creation, background recreation, contextual Add and metric logging; five native
storage/catalog tests with rich custom data; and the complete library flow.
The first combined attempt rejected an old custom record's expanded serialized
shape. A focused comparison confirmed only additive defaults, with every prior
field unchanged. The chart fixture now lets the installed codec serialize those
defaults before taking its strict comparison snapshot. Both attempts remain in
`.amp/in/artifacts/library-integrated/`; only the later complete sequence is a
pass. The broader workout/media/program/onboarding suite is separate evidence.

The later Full Body/exercise-settings baseline passes public and private builds,
lint, 50 JVM tests and six Android tests. The parent Info-sheet media, onboarding,
body-data and library/settings flows passed on one unchanged APK. Full Body
acceptance separately covers all 18 asymmetric fields, manual visual fat,
precision, recreation, invalid restore and complete native document backup.
An executor replacement interrupted the combined run. The recovered emulator
retained the same APK hash, and the unfinished phases were rerun. Original
preferences and private photo bytes were restored exactly.

Failed assertions remain under `.amp/in/artifacts/settings-body-combined/`.
One expected an empty library query after Back, although the query correctly
persisted. Another used a fast drag that skipped Waist in the lazy Full Body
form. The corrected body smoke uses the shorter, slower lookup drag. Onboarding
now checks the selected root before and after shell gestures instead of sending
subsequent taps to stale coordinates. No app navigation change was made for
those test failures. These checks are not an uninterrupted aggregate-suite pass.

The duplicate/Region/lossless-alias/compact-photo integration passes public and
private build and lint, 51 JVM tests and six Android tests. On one unchanged
private APK, the complete photo-sheet/SAF, custom/duplicate, workout/media and
body-data flows passed in sequence. The corrected older photo/SAF flow then
passed separately on that same APK. All eight aliases and 515 characters survive
Review, Create and restart. The public APK contains only three catalog JSON
assets; recovered media and body-reference art remain private.

The first older-photo run checked cleanup before the sheet finished dismissing.
A focused probe still found the imported replacement 23 ms after Back, then
confirmed its removal on return to history, with the saved record unchanged.
That smoke now waits for history before checking cleanup. Both older body/photo
helpers also reject Android's zero-exit failed hierarchy dumps rather than
reading stale XML. Failed attempts, the probe and the complete rerun remain under
`.amp/in/artifacts/combined-duplicate-photo/`; this is not an uninterrupted
five-flow pass. The original synthetic preferences and original private photo
bytes were restored exactly after each driver finished.

The later Type/heading integration passes public/private build and lint,
51 JVM tests, and six Android tests plus focused offline navigation on each
variant. Private-font inspection caught an Info title still using root-display
typography; the corrected detail role now shows the full Copy suffix at normal
and 1.3× text. Root header crops are pixel-identical to the controls. Semantic
and rendered checks remain separate in `type-heading-integrated/`; these are
focused follow-ups, not a full five-flow rerun on the newer APK. See the
[heading integration report](reviews/exercise-heading-overflow-01.md).

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

The body-data smoke check uses a separate disposable emulator with no body
records. It exercises native input, editing, deletion, restart, enlarged type,
and Android document export/restore with synthetic data:

```sh
python tooling/android_body_data_smoke.py --serial emulator-5582 \
  --output .amp/in/artifacts/body-data/ui
```

It requires no reference media, records the installed APK and offline network
state, and restores network/font settings in `finally`. `BodyDataTest` covers
calendar-date and legacy-backup behavior. `BodyDataStorageTest` checks a new
store/view model, full restore, invalid-restore atomicity, and preservation of
nonempty workouts and programs.
