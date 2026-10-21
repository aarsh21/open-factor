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
