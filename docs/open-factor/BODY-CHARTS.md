# Raw body charts and range controls

Body Data → Scale Weight or Measurements → View Charts opens charts of saved
readings. The metric selector includes scale weight, manually entered scale body
fat, separate manual Visual Body Fat, and all 18 circumference fields. Weight
follows the kg/lb setting. Circumferences remain in cm. Changing the chart does
not change saved data.

Each dot represents a saved record. Same-date records remain separate. Missing
fields are omitted, not converted to zero. A recorded 0% body-fat value remains
visible. Dots and accessible record rows select a dated value. The full records
remain editable through Body Data. Identical date/value dots can overlap; the
rows still select each record separately. The vertical axis fits the visible
values, so different metrics and ranges can use different scales.

## Source observations and local policies

The retained original `10-body-data.mp4` shows the Weight Trend destination at
approximately 112 seconds. Its controls read 1W, 1M, 3M, 6M, 1Y, and All. The
chart has Scale Weight and Trend Weight legends, a daily selector, Average and
Difference summaries, and a tutorial entry. One selected week spans
Monday–Sunday. The recorded range interaction does not establish a completed
range change or its date-window and aggregation rules.

The local charts deliberately plot raw records, not a substitute Trend Weight
series. Their title and help state that no smoothing or estimated trend is
calculated. No average, difference, interpolation, daily aggregation, or missing
value is invented to imitate the source. Circumference and manual-fat charts
are local extensions of the recorded field inventory, not recovered populated
source charts.

The local range policy is explicit:

- 1W is the current Monday–Sunday week.
- 1M is the current calendar month.
- 3M, 6M, and 1Y include the current month and the preceding 2, 5, or 11 months.
- All spans the earliest through latest saved dates for the selected metric.
- Both endpoints are inclusive. Future-dated readings appear only inside the
  displayed dates. An empty All range uses today for both endpoints.

The selected range survives Back and reopening during the current app session
and Android activity recreation. It is not a durable settings preference and
resets to 1W after a force-stop. Weight and measurement entry points select
Scale Weight and Neck respectively. Existing records remain durable and covered
by the existing complete JSON backup; there is no new persisted chart model.

The first chart entry opens the existing About body charts dialog. Got it,
outside dismissal, and Back record the `bodyCharts` tutorial dismissal in
preferences. Later entries do not reopen the dialog, but the help icon remains
available. Reset Tutorials clears that dismissal. This is a local raw-chart
adaptation of the observed tutorial entry, not recovered source tutorial copy
or sequencing. Data Visibility does not hide historical chart series.

## Remaining source parity

Weight Trend smoothing, source aggregation, average/difference policy, daily
selector behavior, chart gestures, and tutorial sequencing remain unrecovered.
The [Full Body record policy](BODY-MEASUREMENTS.md) distinguishes the observed
input and reference sheet from local storage and manual-fat chart behavior.
The existing progress-photo workflow is unchanged. Private source frames and
account data are not bundled or committed.

## Reproduce local verification

Build the native app from `openfactor/` with `:app:testDebugUnitTest`,
`:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest`.
Run the Android tests on a disposable emulator, then run:

```sh
python tooling/android_body_charts_smoke.py --serial emulator-5582 \
  --output .amp/in/artifacts/body-charts/ui
```

The smoke requires an undismissed body-chart tutorial. It exercises first entry,
dismissal, and reentry, then checks synthetic records created through the real
forms. It checks six ranges against their dates and independent record counts, same-day
readings, missing versus zero, kg/lb and cm, dot and row selection, large text,
Back/reopen, and restart. It verifies that preferences, photos, workouts,
composition drafts, programs, and all other unrelated app data remain unchanged.
It restores the prior font, weight-unit, and network settings and leaves its
synthetic records for review. The existing weight/photo smoke scripts are
unchanged. Keep runtime data, screenshots, and private evidence out of Git.
