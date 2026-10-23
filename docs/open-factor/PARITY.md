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

### Derived analytics recovery

The [derived analytics report](reviews/derived-analytics-01.md) records partial
ARM64 recovery of persisted-set eligibility, side-volume arithmetic, strength
curves and Weight Trend initialization. Twelve synthetic-memory checks execute
the original scalar helper bytes. They distinguish skipped from empty logs and
verify full plus one-third partial repetitions for volume. Source session
isComplete and persisted isSkipped are different fields with an unresolved
conversion; local completion must not be presented as that source policy.

Ordinary paired volume sums both side workloads; strength uses a separate side
mean. Persistent-per-side volume has another branch. The recovered curve and
smoothing fragments do not establish their complete caller contracts. Weight
Trend, records and estimated 1-RM remain unavailable, not accepted as equivalent
to conventional substitute formulas. This is binary evidence, not a populated
source UI comparison or a combined application acceptance pass.

The logical-set consumer change passes 95 JVM tests and a bounded offline
worker-APK native check at normal and 1.3 font scale. Counts group sides and
Myo components while Dashboard history retains their distinct completed
actuals. Both final history captures were inspected. The full original APK
pair, private files and device settings were restored and independently
rechecked before releasing emulator-5580. The report retains the earlier
fixture-serialization failure separately from the corrected passing run.

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

The manual editor now accepts a variable normal-cycle count and explicit target
save scopes. Program sessions keep performed values blank, separate from
prescriptions. Static prescriptions are not Smart Progression.
Deload placement and explicit overrides now persist, and the program library
starts derived or explicit deload sessions through the real caller. The local
draft Save/Cancel policy is an adaptation. The worker's 93 JVM tests, six native
TrainingFlow tests, and synthetic UI checks pass. Affected normal and 1.3×
renders were inspected. Parent combined-APK acceptance remains separate. See the
[deload contract and progression limits](PROGRESSION-DELOAD.md).
The selected-template materializer and deload transformation do not recover
exercise selection, the history-dependent strength profile, or calendar placement.
See [manual programs](PROGRAMS.md) and the private `program-recovery/REPORT.md`.

## Corrupt playback-cache recovery, October 23

The unchanged private APK failed after each Chin-Up camera cache was truncated
to 64 bytes or given a zeroed header without changing its length. Healthy-camera
controls passed before both failures. The corrected player rebuilds a bundled
clip's cache once after a native playback error. All three cameras then autoplay,
and the rebuilt files hash-match the intact originals in both cases.

This bounded native check used private APK SHA-256
`b954704349b46060866b567633747fd5c4e775c4dcf94938a3e55b6c9dc4a780`.
Its 89 JVM tests, lint, and both APK assemblies pass. Incremental packaging hit
the documented large-archive integer overflow. A clean rebuild passed without
an app workaround. These results precede the newer combined feature integration.

A separately signed control APK changes only one bundled clip to 64 bytes.
Its executable code matches the candidate. The player shows the photo fallback
without an Android error dialog, makes no repeated cache writes over eight
seconds, and can switch to a healthy camera. The shared reference pack is
unchanged. This control does not certify every codec or storage-failure case.

Evidence is under `.amp/in/artifacts/astra-parity/media/`, including the before
and after results, rendered captures, control identity, and independent handoff.
Original app and test APKs, the complete private tree with ownership and modes,
both original photos, and device settings match the baseline after restoration.
This is local emulator acceptance, not physical-device or full source parity.

## Private combined-build acceptance, October 23

The integrated product at
[`fb979767`](https://github.com/aarsh21/open-factor/commit/fb97976719fbcebc7579ab610a03ddcbcc4e9fcb)
passes 110 maintained JVM tests, with no failures, errors, or skips. Lint reports
zero errors and eleven warnings, and both APK assemblies pass. The subsequent
test-only correction at
[`d493af60`](https://github.com/aarsh21/open-factor/commit/d493af60777259a1e279f0c948b4dbff80a3a176)
checks catalog generation-fact enrichment on device without changing the product.
All native runs below pin that revision and the installed APK pair. Product and
native-test source files remain byte-identical through these runs and the later
driver and documentation corrections.

Fifteen representative offline cases pass across corrected replays on three
emulators. This is the same compiled build with two signing variants, not one
uninterrupted single-APK suite pass. All eleven native instrumentation tests pass
on both variants. Not every UI case runs on both variants.

The assembled private application and test APK hashes are respectively
`7deb516b808b37fba8dd365b4ca7eb860f14455d3da247df600f9e81e509e35b` and
`6baf8daa782c16ed9e62286d57a7a33900f2d343155b03e647ee66d73093a204`.
Emulator-5582 requires baseline-compatible signed copies, with hashes
`e8f18e0d1e840bb5b55096beb8dcd59807bd453408e068f615122701b247adf5` and
`12307dfc1050200a787aa945d29da6877257ba16196ed238e524f0ae6d3b5b8b`.
All 5,674 entries of the assembled application APK and all nine test-APK entries
byte-match their signed copies. Each copy adds only three V1 signing files.
These are APK entry counts, not counts of original reference files.

Evidence is locally excluded under `.amp/in/artifacts/astra-parity/`.
`acceptance-summary.json` indexes every attempt and these passing case receipts:

- `combined`: contract, library, photo-sheet, and portability.
- `replay-5580`: workout-media, program, and composition.
- `replay-5584`: custom, metrics, and charts.
- `replay-full-body-photos`: photos.
- `replay-followup-5582`: onboarding and Health UI only, without transfers or grants.
- `replay-body-final`: body data.
- `replay-full-body-observed`: illustrated full-body measurements.

All 32 case attempts remain retained, including seventeen failed attempts.
Corrections target the actual controls: expanded Shortcuts, RIR menus, the
separate Units page, the Health panel, an explicit existing gym, and first-entry
chart help. Tutorial dismissal must change only its expected preference.
Empty backups may omit `photoAssets`, as the writer deliberately does. Exact
original-photo byte equality still rejects omitted nonempty assets, changed
bytes, missing photos, and unexpected photos. Seven host mutation controls pass.
The ten corrected drivers match their executed copies, apart from retaining the
portable Health artifact-root expression. `driver-source-equivalence.json`
records that comparison. No product behavior changed to satisfy these drivers.

One full-body child passed its UI and data checks but failed the enclosing
post-case APK pin in `replay-full-body-complete`. That attempt did not record
the actual mismatched hash, so its cause remains unverified. The unchanged
child passes again in `replay-full-body-observed`, with all six explicit APK-pin
observations matching. The failed receipt is not counted as a pass, and the
pin checks remain strict.

The corrected Health run records only AndroidX `files/profileInstalled`
metadata drift before recovery. It restores the complete archive and rejects
any other user-file drift. The earlier failed Health attempt lacks an after
archive, so this later observation does not establish that earlier failure's
exact cause.

All nine outer runs and their enclosed suites pass restoration, including the
runs with failed cases. Independent final checks confirm original APK pairs,
private bytes, types, modes, UID/GID, external file names and bytes, and settings
with key presence. Emulator-5580 retains 21 private entries and two photos;
5582 retains 22 entries and twelve photos; 5584 retains thirteen entries and no
photos. All three apps are stopped and their leases are released. The separate
`independent-release-5580.json`, `independent-release-5582.json`, and
`independent-release-5584.json` receipts record the checks. These are app-boundary
checks, not bitwise emulator-image comparisons.

The inspected `review/index.html` illustrates the bounded worker-APK UI checks,
not the later combined runs. See the [native UX report](reviews/native-ux-adversarial-01.md).
Full generation, history-based progression, and derived analytics remain
incomplete. New movement, ranking, precondition, and equipment checkpoints remain
separate from this tested product. No physical-source-device operation, Health
grant or transfer, shared-pack edit, private-original publication, or push is
part of this acceptance round.

## Earlier combined integration checkpoints

The preceding source-only snapshot at
[`c663f01d`](https://github.com/aarsh21/open-factor/commit/c663f01df672c5aabb7996649ebe27ef49c38abd)
adds action-specific accessibility names and selected-color states to shortcut
color controls without changing their appearance. All 88 maintained JVM tests,
lint with no errors and eleven warnings, and both APK assemblies pass. All 75
tracked `openfactor/` files byte-match the build copy. The public APK contains
only the three factual JSON assets and has SHA-256
`d9e1b8e4845ec6f65a8603fdb8846f095ef712170c67b4202e688bf10c65adaa`.
Evidence is under `.amp/in/artifacts/parallel-parity/settings-semantics/`.
The semantic-only native follow-up passes on this exact candidate. Direct
`AccessibilityNodeInfo` reads confirm action-specific color descriptions and
Teal versus Default states on the owning clickable controls. This checks
accessibility metadata, not spoken TalkBack output or a full functional rerun.

The preceding source-only snapshot at
[`41cfff2b`](https://github.com/aarsh21/open-factor/commit/41cfff2b38e2d65c98a21eee2e1e86aaa1374586)
includes the Dashboard side-caption and Health dialog-inset corrections. All 88 maintained JVM
tests, lint with no errors and eleven warnings, and both APK assemblies pass.
The parent compared all 75 tracked `openfactor/` files with the isolated build
copy and found no differences. The APK contains only the three factual JSON
assets. Its SHA-256 is
`189a0948553cf7ffdf696b75a53389fbbef59e0b279772b2dec3bc900c389b2f`.
Evidence is under `.amp/in/artifacts/parallel-parity/health-ui-final/`.
Health's corrected UI-only confirmation passes at normal and 1.3× font. Parent
render review confirms no Settings bleed behind the gesture bar and complete
footer and rationale endings. The separate Dashboard render check passes on
this same APK at normal and 1.3× font, with both side captions on one line and
equal 80-pixel heights at 1.3×. Parent inspected those captures and the enlarged
grouped Levels rows. Full Dashboard functional acceptance remains pinned to the
earlier APK below. Exact baseline restoration is verified after both runs;
emulator-5584 is released. Export's bounded worker-APK native checks also pass,
with exact baseline restoration and emulator-5582 released. Settings functional
checks pass on this APK, including one native preference-storage test, theme
contrasts, unit/clock consumers, rest editing, shortcut persistence, logger
display switches and Keep Alive transitions. Parent inspected representative
renders and the window-state dumps. Exact original app/test APKs, XML, private
file manifests and device settings are restored; emulator-5580 is released.
No native lease or build remains active in this validation round.

The Health-inclusive product source at
[`73c34e46`](https://github.com/aarsh21/open-factor/commit/73c34e461b5d994080ab8b69a416fd3fa3f57f45)
passes all 88 maintained JVM tests, lint with no errors and eleven warnings,
application assembly, and Android-test APK assembly. The worker verified an
isolated source overlay. The parent independently compared all 73 tracked
source and build files with local main and found no differences. The public
APK contains exactly `catalog-provenance.json`, `catalog.json`, and
`equipment.json`, with no private originals. Its SHA-256 is
`5b6b20153117eb178b05d348ce7ae119c2ddd4e7ecdf2b813f9abf35d4f71aae`.
Evidence remains locally excluded under
`.amp/in/artifacts/parallel-parity/combined-health/`.

The parent also ran all four tasks directly on local main at
[`2f3d9b14`](https://github.com/aarsh21/open-factor/commit/2f3d9b143a714f247090fd1f0b6b5d307eeb744c)
with the intact private reference pack. All 88 JVM tests pass with no failures,
errors, or skips. Lint reports no errors and eleven warnings. Both APKs build.
Every one of the 5,535 packaged reference files, including all 1,831 original
videos, hash-matches the local pack. The private APK SHA-256 is
`b6778465b9734c2f92df5037f12ba43feb13fbed6d76b01098e2312544c4040f`.
Its build log, test reports, lint report, and verification receipt remain under
`.amp/in/artifacts/parallel-parity/combined-private/`. This build does not
constitute a native run, and the private APK is not published.

The earlier source-only snapshot at
[`96f69fe6`](https://github.com/aarsh21/open-factor/commit/96f69fe6bd3f7125879a68e2b65ec1500e877c91)
passes 82 JVM tests, lint with no errors and nine warnings, application assembly,
and Android-test APK assembly. Eighty tests belong to the maintained suites.
Two temporary tests check combined backup retention and the independent
864-case materializer matrix with 21,168 set targets. The latter also checks
separate press and row deload composition, not a visible deload workflow.

That earlier public APK also contains only the three factual JSON assets.
Its SHA-256 is
`18202bbcf6f7007030b6ab6f01defe175c36183e433d7e02f828302adeced569`.
The build log, source identity, XML reports, temporary test sources, and both
APKs remain locally excluded under
`.amp/in/artifacts/parallel-parity/combined-public/`.

These builds combine the normal programs, lifecycle, Dashboard, Levels, Settings,
body consumers, Gym inventory, scoped export, and logger changes. They also cover
the Dashboard chart-tutorial callback and archived-exercise filtering in
RoutineBuilder. On the exact earlier public APK, TrainingFlow passes five native
tests. A separate RoutineBuilder UI check excludes archived candidates, saves
only the active candidate, and preserves an existing archived reference. Other
combined native checks remain in progress. Worker-native results below apply
to their recorded APKs, not to the whole combined application.

Eight Health contract tests cover import retention, dates, ties, paging,
permissions, failures, stable IDs, version counters, and workout mapping without
a real provider. No health grant or real health-store operation has run.
All integration commits remain local and unpushed.

## Acceptance matrix

All local acceptance cases must run without an active default network. "Partial"
below is not an acceptance pass.
Rows retain earlier per-surface checkpoints. The October 23 combined results
above supersede older driver-failure notes, not unresolved source-parity gaps.

| Surface | Evidence-backed state | Remaining acceptance work |
| --- | --- | --- |
| Navigation | Nuvio shell retained; plus inside its middle with two destinations on each side. All four roots and shortcuts checked at normal and 1.3× text size; full navigation labels remain visible | System Back and IME clearance still need broader coverage. New customization consumers await combined native checks. |
| Shortcuts | Native checks on the pinned combined APK pass saved order/color/hide/Up Next/reset behavior, Back/Cancel, background/rotation draft retention and restart. The expanded sheet exposes all seven actions at 1.3×, and the configured History route opens. The separate newer candidate passes direct action-specific color-name and selected-state accessibility checks | Spoken TalkBack output is untested. Up Next has an honest empty state, not an inferred schedule. Source scheduling, exact colors, pager composition and Save/reset outcomes remain unrecovered. |
| Dashboard | Ordered focus, section, muscle, exercise and body choices have durable consumers, local habits, reset, raw-chart and history routes. The exact earlier combined APK passes the native driver for active/all counts, customization, hidden-waist retention, Save/restart/draft recreation/Cancel/reset, chart-help persistence and drilldowns. A separate fresh-dump-only recreation check passes. On the newer pinned APK, normal/1.3× renders confirm the corrected side captions stay on one line; customization Save preserves the pinned note and non-preference data. See the [Dashboard review](reviews/dashboard-levels-01.md) | The newer-artifact check is limited to captions and grouped Levels rows, not a full functional rerun. Recent Records detection, Weight Trend, Steps and 1-RM remain unavailable. The compact editor and button navigation differ from source. |
| Workout/calendar | The bounded normal unit passes on exact combined `96f69fe6`, including five TrainingFlow tests. Stable-ID library/routine reorder, Back→Cancel, font recreation, Save and force-stop/restart retain exact order. Native scroll checks reach the last calendar week and library heading at normal/1.3×. Parent inspected both routine renders and final scroll receipts; exact original APKs/data/photos/settings are restored and 5580 released to Settings | Eight-week browsing and Move Up/Down remain local adaptations, not source scheduling, drag/drop or activation behavior. Month-marker capsules are taller than adjacent dates. Library-heading captures do not certify every saved row's layout. A nonempty source time-estimate policy is unrecovered. |
| Manual programs | The bounded normal unit passes native cycle-count Save/Cancel/removal, target Cancel/Back, selected-cycle RIR 1/rest 0, following/program scopes with nonrep sanitation and unchanged Cycle 1, no-edit navigation and library save/restart on exact `96f69fe6`. The final enlarged icon row is fully reachable and Save works. Parent inspected those receipts and renders. Additive deload placement, overrides, old-data compatibility, metric-gated derivation, and sentinel dispatch pass worker JVM and native checks. The synthetic UI flow covers FIRST/LAST/None, custom/reset, no-edit Save, Cancel, restart, missing Metrics, and 1.3× font | Earlier Launcher/driver failures remain separate, not an uninterrupted aggregate pass. The 1–52/copy-last resize policy and deload draft transaction are local. General Week/Cycle selection, source Save/activation/drop/schedule outcomes, exact spacing, advanced sets and Daily/Weekly scroll delta remain unaccepted. Worker deload proof does not replace parent combined-APK acceptance or establish history-based progression. |
| Smart programs | A pure selected-template materializer reproduces the recovered seven-normal-cycle target matrix. The combined temporary check passes 864 cases and 21,168 targets. Deload helpers and persisted program dispatch pass asymmetric worker JVM cases | The materializer has no UI caller. Exercise selection, allocation, and history-based progression remain missing. A target matrix is not a complete generator. Deload persistence is not automatic scheduling or a progression policy. |
| Logger | Local default is one blank metric-specific row. Required actuals, optional rep-based RIR, assistance, seconds and meters persist through restart, finish, history and backup. All 18 catalog metric combinations passed the offline UI flow. The bodyweight badge uses a dated prior weight and the catalog contribution coefficient. Notes use Save/Cancel dialogs; Finish is in Workout options. Converted loads remain readable beside RIR at normal and 1.3× text size | Source custom keypad, independent side sequencing, persistent-per-side behavior, advanced set types, supersets, target plan-update flow and exact layout remain different or unaccepted. Per-side labels do not establish side sequencing. The contribution badge does not establish source volume/progression arithmetic. Empty warm-up insertion does not implement source schemes. |
| Rest | Last-warm-up and between-exercise switches now control rest eligibility independently of auto-next. Native checks cover opposite switch values, deadline retention, uncompletion, explicit zero and restart. The strengthened warm-up-only regression passes in the combined JVM run | Left/right policy, source sheet equivalence and warm-up schemes remain unaccepted. These switches do not implement a general warm-up generator. |
| Live settings | Workout and gym settings return to the session. Timer visibility, bodyweight contribution and distance-unit preferences have logger consumers. Native entry of 1.5 mi stores 2414.016 m and remains readable in history at 1.3× text size | Complete source settings are absent. Showing RIR is not source auto-population under RIR Tracking. Logger document export did not complete because the driver sought Save while DocumentsUI showed SAVE. |
| History/summary | Local completion, repeat, deletion and stored sets exist | Populated source history, summary, record handling, incomplete-set rules and bodyweight-volume calculations are unobserved. Existing local arithmetic is not a source oracle. |
| Exercise library | Bundled catalog, gym/type/laterality filters, grouped browse, name/alias search and favorites exist. Query changes reset position; unchanged-query detail return preserves it. Resistance filters actual equipment alternatives, excluding support-only equipment. Modified uses actual overrides, not pinned notes or Do Not Recommend. Custom-only Archive is reversible, hides new-entry candidates and preserves saved references. Worker public/private native tests and bounded UI flows pass. The exact earlier combined APK passes the RoutineBuilder candidate/preservation check. See the [lifecycle review](reviews/exercise-lifecycle-scopes-01.md) | Scope controls differ from the source tab row. Complete source membership, selected-muscle ordering and ranking remain unaccepted. Single-entity selection and immediate dismissal are local policies. The partly offscreen new saved-routine card is not layout-certified. |
| Exercise detail | Root-library selection opens exercise settings, whose Info row opens a closeable detail sheet without Add. Contextual detail Add preserves draft/program/live-session context; offline program-picker checks confirmed it changes the intended program day without changing an active session. Imported properties, instructions, media and local history exist. Combined offline emulator checks in the new Info sheet passed for Chin-Up autoplay on opening, all three cameras and reopening, instruction collapse and expansion, native 1X/2.5X/5X playback, explicit pause and background reset. Sheet dismissal and settings Back preserve the library query. Clip loading resets for each selected clip instead of temporarily reopening the previous camera. Rendered full-width center-cropped media has no seek bar. Empty-cache fault injection first reproduced a blank unprepared player; all three camera caches rebuild byte-for-byte from bundled originals and autoplay offline | Exact source sheet geometry and history analytics still differ. Physical-device compatibility is not certified. Source autoplay and camera-switch continuity remain unobserved. Nonempty corrupt-cache, decoder and storage failures remain unaccepted. |
| Exercise settings | Durable pinned notes, rest defaults and Do Not Recommend participate in backup. Fresh draft/live/direct/program entries inherit rest defaults, not the pin. The logger displays the global pin separately from editable workout-entry notes; only entry notes enter new history. Existing saved notes remain unchanged without migration. Edit Duplicate opens an unsaved five-step copy; Create assigns a fresh identity without changing the original or inheriting its media, history or defaults. Performed uses completed-set history; Deprioritized uses Do Not Recommend. The UI states that no generator is available | The retained source note editor binds the pin/history distinction, superseding the earlier local copy policy. Source Save, exact pin placement/update timing, duplicate outcomes and scope policies remain unobserved. Equipment-weight overrides and left/right rest remain open. Do Not Recommend does not implement a recommendation engine. See the [pinned-note correction](reviews/exercise-pinned-notes-01.md), [earlier defaults contract](reviews/exercise-settings-01.md) and [duplicate policy](reviews/exercise-duplicate-01.md). |
| Custom exercises | Five-step creation supports explicit metrics, primary/secondary muscles, resistance/support alternatives, ROM/stability, contribution, aliases and review. Wizard edits remain separate until Create. Rich metadata participates in backup and gym availability; caller context is preserved. Recovered Region and Movement Patterns facts preserve catalog identity, including older private packs without rewriting them. Lossless aliases and readable Copy headings retain their earlier native acceptance. Custom archive and override reset pass worker native tests on both variants, public UI flows at normal and 1.3× font, and a private UI flow at 1.3× | Source Create was not executed. Muscle-tap cycling, metric-family restrictions and complete choice menus are local policies or unobserved. No separate private normal-font UI run is claimed. Remaining actions, independent side logging and exact source controls remain unaccepted. |
| Gyms | Category-wide bulk controls, enabled summaries, independent transactional kg/lb lists, quantity metadata, explicit empty lists and recreation-safe drafts are integrated. Worker verification passes seven native tests and bounded offline UI/SAF checks. Parent inspected normal and 1.3× renders. See the [Gym review](reviews/gyms-equipment-01.md) | Source unit/save algorithms remain unknown. Quantity has no inferred loading or availability effect. Saved-profile starters are not claimed as factory defaults, and public icons are approximations. All catalog IDs remain. |
| Body data/photos | The worker's complete photo/visibility/SAF and chart flows pass on its recorded consumer APK. Cases include four caller returns, history return, reachable scrim, hidden precision/photo bytes, all-hidden Save, six ranges, selection, units, first-entry help dismissal/reentry and restart. Separate Back/outside dismissal persistence and private-font/SVG/keyboard probes pass. All nine opt-in SVGs decode. Parent inspected affected normal/1.3× renders and final receipts. Exact incoming 33,004-byte XML, all twelve private photo files, font and offline settings are restored; 5582 is released to Export | These worker flows are not a combined-parent native acceptance pass. Earlier failed driver attempts remain retained. The 48 dp clearance and first-entry raw-chart help are local adaptations. Weight Trend, source aggregation, initialization/estimation and reference conversion remain unrecovered. Source Save, same-day record policy and populated trend outcomes remain unobserved. |
| Levels | Source-backed primary 1/secondary 0.5 weighting, primary precedence and ×7/days normalization for 7/30/90 days are implemented. Upper-body row order and Time Period sheet are recovered. The exact earlier combined APK passes native weighting controls, period-sheet recreation, exact list bounds, header/system Back and duplicate Create→Back. The separate fresh-dump recreation check passes. Parent inspected readable grouped upper-body rows at 1.3× on the newer pinned APK | Complete eligibility/date rules, lower-body order and map colors remain local. Binary recovery and local synthetic fixtures are not a populated source comparison. |
| Onboarding | Local defer/resume, validation and gym/program handoff exist | Source post-login forms were not captured sufficiently for parity. Onboarding body values are not dated body-data records. Account/login gates are intentionally omitted. |
| Settings | The pinned combined APK passes one PreferenceStorageTest and offline native theme/system contrasts, four unit saves, profile-height and clock consumers, actual miles/yards input and metric redisplay, rest Save/Cancel/Back/invalid/IME/restart, shortcuts and logger timer/bodyweight on/off/on. Keep Alive window dumps confirm active/resumed/return on and background/route-exit/disabled/restart off. The newer semantic-only candidate passes direct accessibility-node checks. Parent inspected representative normal/1.3× captures; exact original APKs/XML/private files/photos/device settings are restored and 5580 released. See the [preferences review](reviews/preferences-shortcuts-01.md) | Earlier driver failures remain separate from the corrected checks. Spoken TalkBack and source Save/algorithm equivalence are not claimed. Language and energy units remain explicitly unavailable. Propagation, auto-RIR and source scrolling rules are unrecovered. |
| Data export | Complete backups retain photos, hidden data, all new preferences and imported scale provenance. The recorded export APK passes offline native Quick/Granular Save contents and date boundaries, Back cancellation before/after filename edit, scoped Restore rejection, hidden records/original-byte retention, tutorial dismissal/reopening/reset/cancel, complete SAF backup into an empty store and restore/restart, and offline help search/no-results. One Android photo-storage test passes. Parent inspected representative normal/1.3× captures and restoration receipts; exact incoming XML, twelve originals, Body APK and settings are restored and 5582 released. See [data portability](DATA-PORTABILITY.md) | This worker-APK pass does not cover the newer Workout Settings keys or Health provenance. The picker exposes Back cancellation, not a separate Cancel control. Earlier setup/driver failures remain separate. Calendar boundaries and category mappings are local policies, not executed source outcomes. Steps, Weight Trend and Period are not fabricated. |
| Integrations | Optional device-local Health Connect panel, per-action permissions, manual-first scale merge, steps reads and stable-ID exports are implemented. Combined builds include eight no-store contract tests. The final pinned APK passes native UI-only Settings/onboarding entry and return, rationale and no-transfer checks at normal/1.3×. Parent inspected corrected gesture-inset coverage and complete footer/rationale endings; exact baseline APK/test/XML/profile/private-file hashes and settings are restored. See [Health Connect](HEALTH-CONNECT.md) | No grant or health-store operation ran. Actual unavailable/denied displays and provider transfer/idempotence compatibility remain unaccepted. The 1.3× Settings-entry capture partly positions the row subtitle behind navigation; it proves entry, not full-row clearance or persistent clipping. The earlier UI-only pass, inset defect and correction remain separate evidence, not an uninterrupted aggregate pass. |

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
  --leased-emulator --output .amp/in/artifacts/parity/offline-suite \
  --apk-sha256 "$APP_SHA256" --test-apk-sha256 "$TEST_APK_SHA256" \
  --source-revision "$(git rev-parse HEAD)" --expect-provider "$PROVIDER_STATE"
```

The hash variables must identify the installed app and test APKs. Use `absent`
for an absent test APK. `PROVIDER_STATE` is the observed Health Connect state:
`available`, `unavailable`, or `update-required`. The output directory must be new.
The full suite requires a private build with original exercise media and the
nine opt-in body-reference SVGs. Public builds cannot pass those media cases.

The offline suite runs 15 independent synthetic-data cases on one pinned APK.
It checks no active default network before each flow and preserves failure
receipts. Recovery verifies the original private tree, owned external files,
device settings, and unchanged APK identities. It never installs an APK, clears
the app, grants Health permissions, or transfers provider data. Local checks do
not certify the missing or unobserved source behavior above. Record failures
alongside passes rather than marking the entire parity matrix accepted.

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
