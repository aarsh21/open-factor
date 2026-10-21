# Investigation ledger

## Identity and evidence

The supplied APK is the base APK of `com.sbs.train`, version 1.4.1 (130). Its SHA-256 is `32df7bbb6939444957d150641e48c2a2788f8fd6c85bc111df8d6047cd044396`. The installed phone's base APK has the same digest. REA 6.7.0 pulled the base plus arm64-v8a, English and xxxhdpi splits.

- Artifact inventory: `ev_ea18edda42bfc849aab356fc7f3b459754c430d22461e2c7b256dc469c8b531d`.
- Split acquisition: `ev_60880ccecda24bf84052b53cef8cbc6add2b4909ece9d1791d584ce765fd4393`.
- Raw evidence and extracted content remain in the parent workspace's `inspection/private/` directory, outside this public repository.

## Observed packaged content

| Category | Files | Evidence boundary |
| --- | ---: | --- |
| Exercise images | 1,832 | WEBP exercise frame images, not a count of exercises |
| Exercise instruction files | 1,500 | Markdown; ID-to-name catalog still under investigation |
| Equipment images | 297 | PNG files named with IDs |
| Muscle diagrams | 44 | Male/female SVG overlays |
| Body avatars | 4 | Front/back SVGs |
| Rive animations | 2 | App presentation animations |

There are no bundled `.mp4` or `.webm` files in the base APK inventory. This does not establish that the running app has no streamed or downloaded videos.

## Tool setup verification

The user approved REA setup for Codex. `setup --client codex --yes` backed up the Codex config, registered the version-pinned MCP server and installed the matching skill. Scoped `doctor --client codex --skill` reports healthy. A direct MCP initialize/tools-list handshake identified REA 6.7.0 and 207 tools. The existing Codex session needs a restart to expose newly registered tools, so investigation continues through the version-pinned CLI.

Optional Ghidra/Hopper/IDA/JADX/Kuna engines are absent. The base APK contains no `libapp.so`; its arm64 split does. REA Dart AOT inspection of the split reports unrecognized snapshot symbols. This is an analysis limitation, not proof that exercise metadata is absent.

## Runtime observations

- Welcome screen includes Log In and Get Started.
- Onboarding includes Basics, Gym & Equipment, Program.
- Basics offers optional Health Connect sync for steps, weight, body metrics and workouts.
- Full authenticated screens are pending completion of onboarding/sign-in by the user.

## Parity status

Every unobserved flow remains unverified. No one-to-one completeness claim is made at this stage. Update this ledger as screens and behavior are captured.

## Catalog recovery and runtime update

The packaged import member `assets/flutter_assets/packages/macrofactor/assets/import/app_file.json` provides 1,345 unique exercise records and a 5,301-entry UUID index. It resolves all exercise metadata references, including 297 equipment entities, 35 resistance equipment groups and 53 support equipment groups. Its SHA-256 is `65f1d2450e6a3c01542210705b929cf4c9544af1cadfd6374474fd1633c1e00f`.

Exact ID joins resolve images for 619 exercises and instructions for 1,213. The local import retains complete raw metadata and equipment/group structures; the UI projection exposes aliases, muscles, equipment and biomechanical properties. Available still images and instructions are loaded locally. Video retrieval remains unresolved.

After the user signed in, runtime captures established:

- Main areas: Dashboard, Workout, central Shortcuts action, Levels, More.
- Dashboard: weekly muscles/sets/exercise targets; active-program/all-workout scopes; insights/analytics, habits and body metrics.
- Workout: weekly calendar, active program, workout library, program blocks, rest days and muscle labels.
- Shortcuts: Weight, Photos, Metrics, History, Up Next, Empty Workout, New Program, New Workout.
- More: Account, Subscription, Integrations, Units, Language, Dashboard/Workout/Weight Trend/Exercises settings, Gym Profiles, Theme, Data Export, Data Visibility, Account & Data Deletion, Community & Support, Other.
- Create Workout: scratch/XLSX import, name, gym, target muscles and exercise selection, save-to-library.
- Exercise selection: equipment/type/laterality filters, muscle-image filters, categorized exercise list, search, add, supersets.
- Exercise detail: Instructions/Details/History tabs; camera-angle thumbnails; technique sections; target muscle cards, range-of-motion/stability values, resistance equipment, alternative names, type, bodyweight contribution, metrics, laterality and joint actions.

These screenshots remain private because they include a signed-in personal account. A temporary creation wizard was inspected without saving a new workout to the source account; restarting the source application returned to Dashboard.

## Implementation parity matrix

| Surface or behavior | Status |
| --- | --- |
| Nuvio-derived capsule navigation and settings grouping | Implemented; device visual review pending |
| 1,345-entry reference catalog and available local images/instructions | Implemented through ignored local import |
| Basic exercise search, aliases, muscles/equipment, favorites | Implemented; source filter set is broader |
| Exercise properties, target muscles and available media | Partial; videos and exact source media layout pending |
| Local workout templates and custom exercises | Implemented; source creation wizard/XLSX flow not yet matched |
| Weight/reps/RIR, warm-ups, notes, rest timer | Implemented; source logging layout and full set options pending |
| Workout completion, history and repeat | Implemented; records/summary parity pending |
| Units and JSON backup/restore | Implemented; source export formats differ |
| Dashboard | Partial; source customization and full metrics pending |
| Program builder, periodization, automatic progression | Pending |
| Gym profiles and equipment customization | Pending |
| Weight, photos and body metrics | Pending |
| Levels and game | Pending |
| Health Connect, remote sync, localization | Pending |

No complete-clone claim is supported by the current state.

## 21 October: authenticated continuation

The source Levels page shows front/back anatomical figures, average weekly sets over week/month/three-month windows and muscle-group drill-down. A local implementation now counts completed working sets by primary/secondary group; the source's exact weighting, scale and aggregation algorithm remain unverified. It is not a game screen.

Source gym profile captures establish icon/name, duplicate, selected/all equipment, weight selection editing and prefill choices. Static metadata contains 297 equipment entities; visible Everything Gym reports 292. Preset flag counts and visible row counts do not always match, and this remains open.

The actual active logger was captured separately from workout setup: horizontal exercise strip, single exercise view, weight/reps controls, and Pause/Minimize/Finish/Settings/Gym/Discard menu. The current multi-exercise local logger is not a 1:1 rendition of this screen. The temporary source workout was discarded through the confirmation UI without saving. Source phone stay-awake setting was restored to its original value (0).

Static recovery resolves 1,831 ordered video paths for 619 exercises: preferred thumbnail first, then suffix. All paths/order match the independent recovery. A standard anonymous Firebase metadata request for the bench-press preferred object returned HTTP 401, `Firebase App Check token is invalid`. No source video bytes or working source URLs have been verified. The importer supports caller-supplied matching MP4 exports; the UI retains photographic camera choices and labels still-only previews.

The foundation review disposition is **fix** (`reviews/foundation-01.md`). Scope selection, customization toggles, first-viewport resume, persisted rest deadline, neutral text, label weights, logging semantics and the five-block direction contract were corrected. A fresh review verdict is still pending; complete clone acceptance remains open.

Gym profiles now persist locally with name/icon, duplicate/delete/default selection, literal preset prefills, selected/all equipment search and editable weight lists. Exercise browse selects a gym and applies raw resistance/support groups (all members within each group, alternatives between groups), plus Type and Laterality metadata filters. Source allow/disallow overrides, exact preset visible counts, default weight generation, weight/plate calculator and per-workout gym selection remain open.

## Native browse predicate and source program pass

Static native recovery establishes five browse groups (Bodyweight & Personal, Free Weights, Machines including cables, Cardio, Other), equipment-category predicates, a boost/name/ID baseline comparator, section ranking by first-five promotion totals, and collapsed groups of five when the filtered count exceeds eight. The importer now applies that predicate and preserves multiple category matches. All 1,345 joins match the independent research. Static totals are 297/530/500/11/7; authenticated captures imply 294 bodyweight and 527 free-weight exercises. The six-exercise effective-catalog discrepancy and account-specific search promotions remain unresolved; no arbitrary IDs were dropped to fit the counts.

The public source now includes all 1,345 descriptive exercise records and 297 equipment entities with group/preset facts, without original instructions or media. The local importer retains all font families declared in FontManifest, including icon fonts.

Authenticated source program capture: creation methods Smart Generation, Build From Scratch and XLSX; goal; weekly frequency; split; session duration; rep periodization; muscle emphasis; deload and experience; name; color; then an editable cycle/day draft. A three-day generated draft at 40–60 minutes used seven training weeks plus deload and five exercises per workout. Manual creation starts with one rest day, Add Day, per-day gym/rename/reorder, and adding exercises converts a rest day to a workout. Its initial exercise has one Standard Set over seven cycles. The set editor has min/max reps, RIR, skip, rest timers and apply-to-exercise/day/program options. Neither inspected draft was saved or activated, and the source returned to its original dashboard. Native generator rule recovery remains in progress; UI observations alone do not establish that algorithm.

## Runtime workflow change

User requested emulator-only testing through T3 Code device setup, plus a reusable recording covering all accessible source-app features. Further phone operations stopped; its original stay-awake value is restored. T3 `device_list` and `device_open(platform=android)` both returned `Agent device access is turned off for this environment.` The existing disposable emulator is being prepared while the Device panel setting is unavailable. The next source pass must create a feature-indexed recording and a coverage ledger, explicitly retaining any inaccessible screens as gaps. Do not label a partial recording complete.


## Indexed reference tour and emulator integrity boundary

A user-authorized single phone recording pass is finished: 14 chapter groups, 19 original chunks. The private viewing copy has 14 embedded MP4 chapters, an HTML chapter player, JSON coverage/offsets and a Markdown feature index. It runs approximately 28 minutes after excluding identified unrelated notifications, launcher interruptions and one idle chunk. Full decode passed and sampled contact sheets were reviewed. The video and source account data remain outside this repository. `REFERENCE-TOUR.md` documents regeneration and coverage limits.

The tour includes dashboard/customization, calendar/program views, exercise filters/details/media, workout creation, smart/manual program drafts and set editors, a temporary live logger, Levels, body metrics/photos/weight trend, gyms/weights/icons/presets, settings/export/visibility and custom-exercise/assessment drafts. No source workout history was saved; temporary logger data was discarded and drafts were abandoned without activation/creation. The phone session is closed and its original stay-awake value 0 was restored. Populated history/records/summary/Levels, every metric/set type, imports, exports, cloud synchronization and other account-dependent outcomes remain unverified. This is not a full parity acceptance.

On the original Google APIs Android 15 emulator, source login returned a device-integrity error. It has Play services but only a LicenseChecker stub for the Play Store; the supplied APK is sideloaded. A separate official Google Play Android 15 image was installed to permit the supported Store installation route. Source authentication remains pending the user's private Store sign-in and a real login check; a Play Store image alone does not establish a passing integrity verdict. No source APK patching, integrity spoofing, or phone token transfer was performed. Open Factor itself continues to build and run independently on the T3 paku emulator.

The bounded generator research pass is complete: 10 enums/78 constants, 17 split/frequency variants with 69 day templates, conditional rep-center branches and seven-week index/RIR schedules. Exact exercise selection, movement predicate, time/focus allocation, No Preference policy, general deload transforms and smart progression remain unresolved. These tables have not been substituted for a recovered complete generator.

## Logger continuity checkpoint

The logger now renders one selected exercise below a horizontal exercise strip. Selection is persisted, and Pause/Resume/Minimize/Finish actions are available in its menu. Paused time is excluded from elapsed training duration and survives backup/restart. Weekly ring labels use the stronger source hierarchy. Unit/build/lint and native smoke checks are recorded with this checkpoint. The remaining source logger target editor, custom keypad, metric-specific layouts, complete set types, rest sheet and exact spacing remain pending; the existing notes/card/footer layout is still an implementation difference. Actual TalkBack speech verification remains open.

The user subsequently reported the same login problem on the Play Store device and explicitly stopped that investigation. Continue from the indexed video and static evidence; no further source login, credential capture or phone pass is planned. The Device Hub's supported PNG capture mode was selected for the black-viewer investigation, but its display outcome was not verified before the stop request. Do not report source emulator login or the T3 viewer as fixed.


## Logger duration and auto-next correction

Saved summaries now use the same pause-excluding elapsed calculation as the active timer. Finishing while paused closes the pause using one finish timestamp. Exercise Auto-Next is persisted in preferences (enabled for older backups by default); completing an exercise's last unfinished set selects the next unfinished exercise and starts rest in one stored update. The captured two-exercise wraparound establishes the basic behavior; circular order for larger lists is the reconstruction policy and is not an independently recovered complete source algorithm. Unchecking a set does not move selection or restart rest. A setting disables auto-next.

Verification: build/lint and 13 unit tests pass, including long pauses, finish-while-paused, stored history, auto-next disabled, partial completion, all-complete behavior, and backup compatibility. A separate two-exercise emulator pass verifies explicit selection survives a process restart. Full parity, source summary equivalence, exact logger metrics/keypad/rest/targets, catalog-count reconciliation and actual TalkBack speech remain open.


Independent `logger-04.md` closes the saved-duration P2 and establishes no material correction regressions, retaining overall **fix** for catalog counts and accessibility/recovery verification alongside unfinished full parity. A later parent-run emulator check supplies visible invalid-reps/recovery evidence; this narrows the recovery gap but does not establish TalkBack, other metric recovery, or alter the independent verdict. Design tokens and remaining drift are recorded from the implemented source in `DESIGN.md` and its sidecar.
