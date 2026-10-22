# Scoped export, data visibility, and offline help

Data Export writes an analysis document. Export Backup writes a complete,
restorable backup, including private original photo bytes. The workflows are
independent. Scoped documents have `format: open-factor-scoped-export` and
`formatVersion: 1`, but no `schemaVersion`. The existing complete-backup decoder
rejects them. Neither workflow filters saved records by visibility.

## Recovered controls do not establish executed outcomes

The retained private `feature-tour/indexed/13-data-support.mp4` shows Reset
Tutorials around 8 seconds, Quick Export around 26 seconds, Granular Export
around 34 seconds, Data Visibility around 47 seconds, default body metrics and
photo controls around 52 seconds, and the Period control around 67 seconds.
The retained `phone-media-extraction-2026-10-22/export-options.xml` independently
contains the Quick/Granular tabs, Last 7 Days, and the Quick inclusion summary.
The source export, reset, and visibility save outcomes are unobserved in that
recording. No source phone or account was changed for this implementation.

The retained ARM64 `libapp.so` has SHA-256
`870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`.
Its `ExportTimeFrame` references 142430 through 142435 and code at
`0xe1c4a0..0xe1c6c8` establish six labels in order: Last 7 Days, Last 14 Days,
Last 30 Days, This Month, Last Month, and All Time. `ExportMetric` references
142439 through 142466 establish category labels and workout/diet tags, not
successful output contents. Diet-only categories have no local tracking model
and are not offered.

The body visibility builder at `0xf8a86c` groups Scale Weight and Visual Body
Fat as defaults, followed by photos, circumference fields, and ratios. The
callback at `0xf8c044` says default metrics cannot be toggled off and suggests
Dashboard customization instead. The local model enforces that rule even when
an imported hidden-key set names a default field. The recorded Period control
was off, but that does not establish its default. Local `showPeriod = false`
is an inactive compatibility policy. Period tracking and ratio views remain
unavailable.

Private evidence and disassembly stay outside Git. Source media is not bundled
or published with this report.

## The analysis format has explicit local policies

`ScopedDataExport.document` writes only selected payload categories. Dated body
records keep their calendar date. Completed history uses each workout's start
date in the recorded device time zone. Boundaries are inclusive:

- Last 7, 14, and 30 Days include today and the preceding 6, 13, and 29 dates.
- This Month starts on the first day and ends today.
- Last Month covers the entire previous calendar month.
- All Time includes every saved date, including future-dated records.

Library and settings categories are current snapshots, not historical versions.
Quick Export includes muscle-group set records, the active saved training
program, and completed workout history with notes. Granular Export starts with
no selected categories. The user selects categories, then a time frame, then
Export to open Android's native Save dialog. Group selection changes only
available categories. Cancel or Back does not write an analysis document or
change app records.

Local category mappings are:

| Category | Local contents |
| --- | --- |
| Scale Weight | Dated saved weight and manually entered scale body fat |
| Body Metrics | Dated circumference and manual Visual Body Fat records |
| Muscle Groups | Catalog muscle labels and completed, non-warmup set IDs |
| Exercises | Saved exercise set actuals and targets, not calculated estimates |
| Training Programs | Saved programs; only the active program in Quick Export |
| Workouts | Saved routines |
| User Profile | Display name only |
| Exercise Settings | Saved per-exercise defaults |
| Gym Profiles | Saved equipment and weight selections |
| Workout Settings | Weight and distance units, rest duration, automatic rest, RIR, auto-next, keep-alive, timer, and bodyweight contribution display |
| Custom Exercises | Saved custom definitions |
| Workout Log Notes | Dated workout and entry notes |
| Workout Log | Dated history, with notes omitted in Granular Export |

The Granular Workout Log category is a local addition so completed history can
be selected explicitly. Weight Trend, Steps, and Period are disabled with an
explanation. The app does not substitute invented calculations or empty
successes for unavailable categories. Original photos, reference media, active
sessions, and unsaved drafts are excluded. Complete backup behavior is unchanged.

## Visibility and tutorial dismissal survive complete backup

`Preferences.dataVisibility` contains `hiddenBodyMetrics` and `showPeriod`.
The toggleable keys are the 18 existing `BodyMeasurement.key` values and
`frontPhoto`, `sidePhoto`, and `backPhoto`. `weight`, `bodyFat`, and
`visualBodyFat` always remain visible. Unknown hidden keys reject the backup
before replacement. Legacy schema-1 backups without visibility decode to all
body fields visible. There is no historical migration or schema-version bump.

Consumers filter presentation only. Existing hidden values, photos, saved
Dashboard choices, history, charts, and exports remain intact. Complete backups
retain visibility preferences and original photo attachments.

`Preferences.dismissedTutorials` defaults to an empty set in legacy backups.
The only implemented topic is `bodyCharts`. On first chart entry, the existing
About body charts dialog explains raw local readings. Dismissal is durable;
the manual help icon remains available. Reset Tutorials removes only that topic
from the dismissal set, so the next chart entry shows the introduction again.
This is a local raw-chart adaptation of the observed chart tutorial, not
recovered source Weight Trend or tutorial sequencing. There are no root prompts.

Knowledge Base & Support works offline. Searchable articles cover complete
backup, replacement restore, scoped exports, visibility, raw charts, workout
logging, file failures, and safe issue reporting. The articles explain this
build's behavior and limits. They do not promise a source support service.

## Validation

The JVM regression covers independently expected category membership, inclusive
leap-year boundaries for all six scopes, local-time workout boundaries, active
versus inactive programs, asymmetric saved actuals, separate notes, unavailable
categories, and explicit complete-backup rejection. Visibility and tutorial
dismissal round-trip through the existing schema, and legacy defaults remain
covered. The existing Android photo-storage regression now restores onto an
empty store and checks hidden preferences, dismissal, restart, and original
attachment bytes.

Build, native Save/Cancel/Back, tutorial reset, and inspected normal/1.3x UI
results are pending the coordinated integration and allocated emulator slot.
The implementation is local and unpushed.
