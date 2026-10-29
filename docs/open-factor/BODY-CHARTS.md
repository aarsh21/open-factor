# Body charts and weight trend

Body Data → Scale Weight or Measurements → View Charts opens saved body readings. The metric selector includes scale weight, scale body fat, separate manual Visual Body Fat and all 18 circumference fields. Weight follows the kg/lb setting; circumferences remain in cm. Changing the chart does not change saved data. Plus → Log Weight opens a bottom sheet above the current destination and keeps any active workout intact.

## Daily weight and seven-day mean

The full-width weight chart and dashboard trend use the same daily model. For each date, the newest saved manual weight takes priority; otherwise the newest imported weight is used. Future dates, nonfinite values and nonpositive weights are excluded from the weight trend. All underlying records remain editable through Body Data; choosing one daily chart value does not delete other readings.

The chart labels recorded weight separately from the trailing seven-calendar-day mean. For each plotted date, that mean uses available daily readings from that date and the preceding six calendar dates. Missing days are omitted, never filled with zero or interpolated. The mean can use readings before the selected display window. With readings of 73.0, 72.8 and 72.6 kg on three consecutive dates, the last mean is 72.8 kg. This is a transparent local calculation, not a recovered proprietary trend algorithm.

Axes, date labels, selectable points and record rows expose the underlying values. An empty range shows an explicit empty state. Body fat and circumference charts remain raw saved readings: same-date records stay separate, missing fields are omitted and a recorded 0% body-fat value remains visible. Identical date/value dots can overlap; accessible rows still select each record separately. No body-fat estimate is inferred from weight.

## Ranges and navigation

- 1W is the current Monday–Sunday week.
- 1M is the current calendar month.
- 3M, 6M and 1Y include the current month and the preceding 2, 5 or 11 months.
- All spans the earliest through latest dates in the selected series; an empty series uses today.
- Endpoints are inclusive. Weight excludes future readings; other metrics show saved readings inside the selected dates.

The range survives Back/reopening during the app session and activity recreation, but is not a durable preference. Weight and measurement entry points select Scale Weight and Neck respectively. Date-dependent views refresh on foreground resume and local-day changes. The help icon explains the chart without an automatic first-entry dialog. Data Visibility does not hide historical chart series. Complete JSON backup retains the saved records; there is no persisted derived trend series.

Health Connect can import real weight and scale body fat through the single Connect/Sync action. Manual weight takes priority on a shared date. See [Health Connect](HEALTH-CONNECT.md) for permission, provider, refresh and merge behavior, and [body measurements](BODY-MEASUREMENTS.md) for manual Visual Body Fat and circumference policies. Weight Trend and Steps remain unsupported categories in scoped analysis export; they are not disabled because the app lacks live readings or a displayed mean.

## Evidence and historical source observations

The retained private `10-body-data.mp4` shows the original Weight Trend destination around 112 seconds, with 1W/1M/3M/6M/1Y/All controls, Scale Weight and Trend Weight legends, a daily selector, Average/Difference summaries and tutorial entry. That recording does not establish its smoothing algorithm or complete range behavior. The current seven-day mean is an explicit local policy; exact source smoothing, gestures and tutorial sequencing remain unclaimed. Private source frames and account data are not bundled or committed.

Run native unit, lint and connected tests from `openfactor/`; the [0.4.0 release record](releases/v0.4.0.md) names completed checks and their limits. The older `tooling/android_body_charts_smoke.py` expects the former automatic raw-chart tutorial and is historical verification tooling, not acceptance for the current weight-trend UI.
