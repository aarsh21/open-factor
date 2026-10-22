# Preferences and shortcuts

This reference separates recovered source choices from local behavior. It does
not accept complete settings parity. The implementation starts at parent local
`main` revision `e91f7e4f`; remote `origin/main` remains `8ae58e2c` at task start.

## Source evidence

The read-only inspection root is
`/home/buhh/.t3/scratch/2026-10-21-goal-use-rea-to-inspect-ac1aa8ca/inspection`.
No source phone operation, source preference write, or private asset-pack change
is part of this work. Source images and video are not bundled or published.

| Choice | Evidence | Limit |
| --- | --- | --- |
| Shortcut order | Native canonical `ShortcutConfiguration` instance 141410, array 136472, and chapter 12 at about 120 seconds | The array contains Weight, Photos, Metrics, History, Empty Workout, New Program, and New Workout in that order. The captured first four rows say Top Shortcut. Source Save was not executed. |
| Shortcut colors | `ShortcutsSettingsView` at `0xe2a4f8` through `0xe2a540` | The editor uses red, orange, yellow, green, teal, blue, and purple. Local ARGB values are an adaptation, not recovered source colors. |
| Up Next | Chapter 12 at about 120 seconds | The captured switch is on. Program placement and advancement rules remain unknown. |
| Units | `UnitSettingsView` closure at `0xf97bc8` through `0xf98f00` with annotated object-pool loads | The view binds Weight, Energy, Height, Clock, and Distance groups to their enum arrays. This establishes actual editor membership, not only string presence. |
| Imperial distance | `0xf98330`, `0xf98370`; enum refs 143406 and 143407 | The labels are Miles and Yards, and Kilometers and Meters. Short distances do not use feet. |
| Height | Enum refs 143362 and 143363 | Centimeters and feet/inches are height choices. This does not establish conversion of body circumferences. |
| Clock | Enum refs 143360 and 143361; UI labels at `0xf985c4` and `0xf98600` | The choices are 12 Hour and 24 Hour. |
| Energy | Enum refs 143404 and 143405, bound array 136787 | The choices are calories and kilojoules. The local app has no energy log or recovered workout-calorie algorithm. |
| Language | Chapter 12 at about 22 through 28 seconds | Android's source-app picker shows System default, English, and Japanese. Native locale branches also contain other strings, but those branches do not establish additional picker coverage. |
| Theme | Chapter 12 near its end | The three visual choices are System, Light, and Dark. Exact light colors remain unmeasured. |
| Workout behavior | Chapter 12 at about 90 through 102 seconds | Previous Reference displays Any workout. Propagate Changes, automatic RIR Tracking, Superset Auto-Scroll, and Exercise Auto-Next are on in the captured state. These observations do not recover their full algorithms. |
| Workout display | Chapter 12 and `feature-tour/screens/workout-settings-bottom.png` | Keep Alive is off. Workout Timer and Bodyweight Contribution are on. Calendar Scrolling displays Daily Scroll. These are captured selections, not a claim about every fresh source installation. |

Native metadata comes from the retained specimen-bound parser under
`video-recovery/model-analysis/` and `catalog-recovery/object_pool.json`. Its
`libapp.so` SHA-256 is
`870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`.
Static interpretation does not establish runtime Save or reset outcomes.

## Local contracts

The grouped Nuvio Settings and central-plus navigation geometry stay intact.
Dark remains the compatibility default. Light uses local neutral colors, and
System follows the device. `LocalConfiguration` reflects the selected app-night
mode, including when the device and app differ. Detail headings keep their
existing typography role.

Shortcut edits are a draft. Save persists the enabled order, per-action colors,
and Up Next visibility together. Back and Cancel discard the draft. Reset
Shortcuts resets the draft only, so Save is still required. Draft state survives
activity recreation. These save/reset rules are local policies.

The shortcut sheet consumes the saved order and colors. The first four enabled
actions are marked as top shortcuts. Up Next has an honest empty state because
the local calendar has no persisted scheduled-item contract. It never infers a
schedule from a program's first day or completed history.

Weight storage remains kg. Distance storage remains meters. Display and input
convert long distances to miles and short/per-side distances to yards when
selected. Existing metric callers default to metric units. Height affects the
saved profile-height display, not circumference records. Clock units affect
the active workout's start time in Shortcuts.

Keep Alive changes `View.keepScreenOn` only for a visible active logger while
the activity is resumed. Pause/background clears the request, and disposal
restores the previous view state. The feature adds no wake lock or permission.
Workout Timer and Bodyweight Contribution have separate logger-owned consumers.
The optional RIR field remains separate from source automatic RIR Tracking.

Units and workout display controls persist immediately. Rest duration remains
a Save/Cancel draft. Preferences participate in the existing full JSON backup.
Older schema-version-1 documents retain metric, dark, timer-visible, and
bodyweight-visible compatibility defaults. Invalid shortcut IDs, duplicate
order entries, and unknown colors reject before restore changes saved data.

## Unaccepted settings

The UI discloses English-only interface text. It does not advertise a working
Japanese translation. Energy units have no control until there is an actual
energy consumer. Previous-reference scope, propagation, automatic RIR,
superset scrolling, and daily/weekly calendar-scroll semantics remain open.
No toggle claims those algorithms. Rest eligibility, dashboard customization,
data visibility, scoped export, help, and Health Connect have separate owners.

## Verification record

The bounded preference checks pass. This does not accept the unrecovered source
algorithms listed above. All Gradle commands use the shared lock, one worker,
a 1536 MiB heap, in-process Kotlin, and the allocated JDK/SDK. Native checks used
only the explicitly allocated offline emulator-5580, Android API 35. No source
phone, Health provider, grant, store operation, extra AVD, or data clear was used.

- The first build found a photo-slot draw callback reading the newly composable
  muted color. The approved correction captures the color before the callback.
- The second build compiled application Kotlin/Java, then failed on five
  `JSONObject.keySet()` calls in the separately owned export tests. The export
  owner's correction is imported for validation.
- The third build passed all 58 JVM tests with zero failures, errors, or skips.
  It assembled the debug application and instrumentation APKs. Lint then failed
  on one new error: profile height read `StateFlow.value` during composition.
  Commit `0f91274d` changes that read to collected state.
- The reserved corrected-state recheck passed after Programs released the lock:
  all 58 JVM tests, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`.
  It completed in 59 seconds. Lint reports zero errors and nine warnings. The
  genuine log is `build-5.log`; copied JVM XML, lint XML, and APK SHA-256 values
  are alongside it. The lock was released and relayed directly to Lifecycle.

The kept codec test uses asymmetric shortcut order/colors, hidden actions,
contrasting flags, explicit legacy expectations, training records, and separate
entry/pinned notes. It also rejects duplicate/unknown actions and unknown colors.
Metric regression checks distinguish miles from yards and preserve metric and
non-distance controls. The instrumentation test covers fresh ViewModel reload,
complete backup/restore, rejected-restore atomicity, and original-store cleanup;
it passed on-device: `PreferenceStorageTest`, one test, zero failures.

### Native checks exercised real consumers

Functional acceptance used the parent's source-only public APK from local main
`41cfff2b`, SHA-256
`189a0948553cf7ffdf696b75a53389fbbef59e0b279772b2dec3bc900c389b2f`.
The test APK hash was
`1112b9d55a08246426629e76fd651f58fc4915c7ab65a5e7cb830e29a7de3ef5`.
The candidate had three factual catalog assets and no reference originals.

- Dark on a light system, Light on light/dark systems, and System following
  light/dark all passed. Render pixels and persisted theme values were checked.
- Pounds, profile-only feet/inches, clock units, and distance units persisted.
  Profile showed 5 ft 11 in for 180.34 cm. Resume Workout changed from 24-hour
  time to 12-hour AM/PM through the real Clock control.
- Real logger input saved 1.7 miles as 2735.8848 meters and 37.5 yards as
  34.29 meters. Switching to metric showed 2.736 km and 34.29 m without changing
  the canonical set values.
- Rest Cancel, Back, disabled invalid Save, saving 137 seconds, restart, and
  keyboard clearance passed at 1.3× type size.
- Shortcut Back/Cancel left data unchanged. Background/resume and rotation
  retained an unsaved draft. Reset affected the draft until Save. History-first
  ordering, hidden Photos, Teal, and Up Next off survived restart and changed
  the actual shortcut sheet. History opened the real history route. The
  expanded default sheet exposed all seven actions in order; Empty Workout
  correctly displayed Resume Workout for the synthetic active session.
- Logger timer and bodyweight badge changed on/off/on through actual controls.
  The separate rest clock stayed present, and stored workout values stayed
  unchanged. Keep Alive requested the window only for the active logger,
  cleared on background and route exit, returned on resume, and stayed off
  after disabling and restart. Settled window dumps verify each state.
- At normal and 1.3× type, inspected renders showed readable controls. The
  floating pill intentionally overlays scrolling content; executed scrolling
  reached Distance, Reset, Cancel, and the last shortcut above the pill/gesture
  region. The Rest dialog retained its actions above the IME.

Native inspection found identical Default names on shortcut color actions.
Own correction `25022a9b`, integrated by the parent as `c663f01d`, adds the action
name and selected-color state without changing appearance. Its exact APK hash
was `d9e1b8e4845ec6f65a8603fdb8846f095ef712170c67b4202e688bf10c65adaa`.
The semantic-only follow-up passed: direct `AccessibilityNodeInfo` reads from
the owning clickable actions returned History color / Teal versus the other
action names / Default. The contextual action opened the correct color dialog.
The functional suite was not rerun against this semantic-only candidate.

Initial driver assertions were corrected rather than attributed to the app:
disabled state belongs to the owning button, not its TextView; window state
needs a settled frame; minimal seeded JSON gains default fields on its first
save; the local Empty Workout label becomes Resume for an active session; and
Compose exports a description proxy separately from its clickable owner. Logs
retain the driver failures. No product behavior was changed to satisfy them.

### Exact incoming state was restored and the lease released

The incoming application and test APKs were independently pulled and hashed
before replacement. Restoration used nonstreamed installs, not uninstall or
data clear. Exact original APK hashes, preference XML bytes, and file manifests
under files/no_backup/shared_prefs, including both original photo bytes, match.
Font 1.0, rotation 0 with auto-rotation 1, system night no with secure key absent,
Wi-Fi 0, and mobile data 0 were restored and read back. The original app remains
force-stopped. The emulator was explicitly released to the parent.

Logs, inspected screenshots, `native-result.json`, `restoration.json`, and
`color-state-accessibility.log` are locally excluded under
`.amp/in/artifacts/preferences/`. No original source media is published.

Own local commits are `fe8abe76` (fields), `12484020` (distance helpers),
`1c9fd247` (theme/shortcuts and Settings controls), `38644f51` (portability row
wiring), `6b308c41` (test correction/explicit legacy assertions), and `0f91274d`
(profile state observation), plus `25022a9b` (color-action semantics). No commit
is pushed. The parent integrates shared dependency originals once; local
cherry-pick copies are not additional features.
