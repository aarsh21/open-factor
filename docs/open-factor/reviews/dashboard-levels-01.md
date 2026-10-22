# Dashboard choices and Levels

The Dashboard now consumes ordered, persisted focus and tile selections. This is
partial source parity, not a recovered analytics engine. Public assembly, lint,
and 54 JVM tests pass. Native validation awaits its allocated emulator.

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

Native normal and 1.3× renders are not yet claimed. The pending native cases
cover Save, Cancel, reset confirmation and cancellation, restart, process
recreation, active-program versus all-workouts scope, raw charts, contribution
drilldowns, and both exercise-settings Back controls. Private observations and
local validation output stay under excluded `.amp/in/`, not in application
assets. Build and asset counts are in
`.amp/in/artifacts/dashboard-levels/build-validation.json`, with the command
output in `build-public.log` alongside it.
