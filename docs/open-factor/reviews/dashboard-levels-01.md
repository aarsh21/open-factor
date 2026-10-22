# Dashboard choices and Levels

The Dashboard now consumes ordered, persisted focus and tile selections. This is
partial source parity, not a recovered analytics engine. The bounded native
functional checks pass on the parent's combined public build, including
customization, chart tutorial persistence, and Levels return state. The caption
correction also passes the separate final-artifact render check.

## Observed controls have local consumers

The retained Dashboard chapter and `dashboard-customize.png` establish Weekly
Workouts and Recent Records switches and ordering, reorderable sections, tile
selectors, exercise search, Steps, and reset. The local editor keeps changes in a
draft until Save. Back discards the draft. Reset changes that draft and requires
Save. Empty selections remain empty after backup and restart.

Sections contain selected tiles in the saved order. Workouts opens saved
workouts in the selected scope. Habits mark dates with saved weigh-ins or
workouts. Body tiles show original dated readings and open the existing raw
charts. Muscle tiles open weighted contributions, then saved exercise history.
Exercise tiles open saved sets without estimating 1-RM. History and body-data
routes remain available. Existing active workouts, drafts, favorites, notes,
body records, and photo references are not rewritten by customization.

Data Visibility hides circumference tiles and selector choices without removing
their saved selections or values. Charts and history do not consult visibility.
Scale Weight and Visual Body Fat remain selectable. Scale Body Fat remains a
raw chart series, not a newly invented independent Dashboard selector.

The captured source defaults include Workouts and Weight Trend insights,
Weigh-In and Workouts habits, Scale Weight and Visual Body Fat, and Steps. Local
muscle and exercise defaults are empty. The source program-derived defaults are
not recovered. The editor uses compact rows rather than the source's preview
cards and drag handles. Root focus switching uses labeled buttons rather than
the source pager. These are presentation differences.

## Native code establishes only part of Levels arithmetic

The retained ARM64 `libapp.so` has SHA-256
`870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`.
Read-only disassembly and the retained Dart metadata establish these facts:

- The helper at `0xd881ac` returns 1 for primary muscle membership, 0.5 for
  secondary membership, and 0 otherwise. Primary membership wins if both match.
- Construction at `0x6d2b70` maps those wrapper lists to imported primary and
  secondary feature-muscle lists. JSON decoding near `0x6c99cc` names the fields.
- The Levels closure at `0xeddc74` multiplies the factor by the selected set-list
  length, divides by duration in days, and multiplies by 7.
- Duration selection at `0xea1cb8` resolves the displayed week, month, and
  three-month periods to 7, 30, and 90 days.

Levels uses that weighting and normalization. It retains the existing local
rules for completed working sets, live-session inclusion, ID deduplication, and
inclusive calendar bounds. Exact source timestamp cutoffs, advanced sets,
independent sides, and the complete set-selection predicate remain unresolved.
The binary trace is not an executed source comparison with populated data.

The retained Levels chapter establishes grouped upper-body rows, the displayed
upper-body order, front and back views, and a Time Period sheet. The local screen
uses those rows and sheet instead of unsupported 20-set progress bars. The
incumbent lower-body ordering and binary trained/untrained map coloring remain
local. Private anatomical assets are not published or added to public builds.

Levels retains period, map direction, sheet, and list state through local
exercise-history navigation. A route-specific saved-state provider and separate
exercise-settings return route preserve that state when exercise settings opens.
The general logger and picker exercise route is unchanged.

## Unrecovered results remain unavailable

Recent Records has its observed empty state and a history route, but no record
detection. Weight Trend does not smooth or estimate weight. Exercise tiles do
not calculate 1-RM. Steps does not invent device records or imply a Health
Connect connection. These choices can be arranged or hidden, but their labels
do not establish functional analytics parity.

## Verification record

The implementation adds a backup contract case with reordered and empty
selections, hidden saved measurements, a live workout, history, favorites, and a
photo reference. It adds an asymmetric Levels case with primary versus
secondary membership, overlap, warm-ups, incomplete sets, duplicate workout IDs,
and dates on both sides of the 30-day boundary. Existing scope and live-session
tests remain unchanged.

The guarded Gradle run passed `:app:testDebugUnitTest`, `:app:assembleDebug`, and
`:app:lintDebug` with one worker and a 1536 MB heap. All 54 JVM tests pass. The
public APK contains 1,345 catalog records, three catalog assets, and no private
reference entries. Its SHA-256 is
`b56209ea21514898da2c486974f4b14828072af80a1cd43989fb9f203e680677`.

The parent combined public artifact has source HEAD
`96f69fe6bd3f7125879a68e2b65ec1500e877c91` and APK SHA-256
`18202bbcf6f7007030b6ab6f01defe175c36183e433d7e02f828302adeced569`.
It includes the normal Settings, program, lifecycle, body, export, gym and logger
units as well as this work. Its XML reports contain 82 JVM tests, including two
temporary evidence tests, with zero failures or errors. Lint reports zero errors
and nine warnings. Both APK assemblies passed. The APK contains exactly the
three factual catalog JSON assets, with no private reference originals.

The offline emulator-5584 run passed these local checks on that exact artifact:

- Active-program versus all-workouts rings show 3 versus 8 working sets from
  asymmetric saved and current workouts. The scoped saved-workout list excludes
  the current session.
- Focus, section and tile order, every selector, exercise search and Steps have
  persisted consumers. Hidden Waist remains saved while absent from the body
  selector and Dashboard. Customization changes no non-preference data.
- Save, restart, unsaved draft process recreation, Back, reset cancellation,
  reset-draft Back and confirmed reset Save preserve their intended boundaries.
- Dashboard chart entry shows help once. Dismissal persists `bodyCharts` without
  changing other preferences or records; immediate and restarted reentry omit
  it, and explicit Help remains available.
- Raw chart, muscle contribution, exercise-tile history and Steps disclosure
  entry/return work. The one-month Chest sheet shows 1.6 and 0.5 sets/week from
  primary and secondary contributions under the documented local eligibility.
- Levels keeps the period, selected contribution sheet and exact Chest text
  bounds through local history, settings header Back and system Back. Duplicate
  cancellation and Create → detail → Back → settings Back return to Levels
  rather than looping. Create adds only the explicitly requested copy.

Two transient UIAutomator null-root messages occurred in the complete run. A
separate follow-up accepted only fresh successful hierarchy dumps and repeated
editor-draft and open-period-sheet process recreation successfully. Its full
state comparison also retained the incoming global exercise note.

Normal and 1.3× editor, period and contribution renders were inspected. The raw
chart and grouped upper-body rows were inspected at normal size. The enlarged
Dashboard exposed an incumbent `Exercises` caption wrapping as `Exercise` / `s`.
The small-ring base caption was changed from 18sp to 16sp, retaining system
scaling and ring geometry.

That correction was verified separately on parent product HEAD
`41cfff2b38e2d65c98a21eee2e1e86aaa1374586`, APK SHA-256
`189a0948553cf7ffdf696b75a53389fbbef59e0b279772b2dec3bc900c389b2f`.
This newer combined artifact also contains the approved Health dialog inset
correction. Its 88 maintained JVM tests pass with zero failures or errors;
lint reports zero errors and 11 warnings, and both APK assemblies pass. Only
the narrow Dashboard caption and Levels row checks ran here on this artifact;
the complete functional acceptance above remains pinned to the earlier build.

The final normal and 1.3× Dashboard and 1.3× grouped Levels rows were captured
and inspected. Both side captions measure 80 pixels high at 1.3× and display on
one line; the planned-count labels align. A subsequent customization Save keeps
the incoming pinned exercise note and all non-preference data unchanged.

The exact incoming 28,869-byte preferences XML, installed APK, zero-byte
`profileInstalled`, font, rotation and network state were restored. The incoming
photo directory was empty; this run does not establish nonempty photo-byte
preservation. The codec contract covers a retained photo reference. The crash
buffer was empty, the app was force-stopped, and the native lease was explicitly
released for the next worker.

Private observations and local validation stay under excluded `.amp/in/`, not
in application assets. `artifacts/dashboard-levels/` contains the isolated and
combined build manifests, `native-96f69fe6-attempt1/` complete run,
`native-fresh-recreation/` follow-up, `native-caption-final/` final render check,
inspected captures and restoration records. `source-recovery/` retains the
private read-only recovery evidence. These are Dashboard/Levels checks, not
combined acceptance of every other unit in the parent artifact. Nothing was
pushed or published.
