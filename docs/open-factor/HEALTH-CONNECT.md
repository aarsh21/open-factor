# Health Connect

Open Factor reads weight, scale body fat and daily steps from the device's Health Connect store. Settings → Health Connect has one primary action: **Connect Health Connect** before access is allowed, and **Sync now** afterward. Android's own consent screen opens directly. Allowing access continues immediately into a real provider read and a durable local save; a second transfer tap or an extra confirmation dialog is not required.

The access icon opens Android's Health Connect settings. Denial, partial grants, provider unavailability and save failures have explicit states. Health Connect is built into Android 14 and newer. Android 9–13 need the provider app; Android 8 can run Open Factor but cannot use Health Connect. The setup action opens the provider's Google Play listing when installation or an update is required.

## Read categories

| Category | Provider operation | Local result |
| --- | --- | --- |
| Weight | Paged `WeightRecord` reads | Dated scale readings and weight charts |
| Scale body fat | Paged `BodyFatRecord` reads, when weight access is also allowed | Body-fat percentage on the matching dated scale reading |
| Steps | `aggregateGroupByPeriod`, using `StepsRecord.COUNT_TOTAL` | Dated step snapshot and dashboard steps |

Only `READ_WEIGHT`, `READ_BODY_FAT` and `READ_STEPS` are requested. Partial access still syncs the allowed useful categories: weight can sync without body-fat permission, and steps can sync without weight permission. Body fat alone cannot create a fictitious weight reading. The app does not prompt again merely because another category was declined; the access icon lets the user change that choice.

Each sync checks live grants before reading. The normal window is the last 30 calendar days including today, within Health Connect's default historical access. No extra history or background-read permission is requested.

## Mapping and persistence

For each local calendar date, the latest valid external-origin weight is selected. Zero or unusable weights cannot replace a valid sample or break the whole sync. Scale body fat uses the latest available reading on that date and retains the existing one-decimal rounding policy. A fat-only date does not create a weight record. Imported records retain a Health Connect source marker.

Manual weight dates take priority. A repeated import updates an existing imported date while retaining its local ID, preventing duplicates. Workouts, photos and body measurements are unaffected. The weight merge and the dated step snapshot are saved in one app-store update, after all selected provider reads succeed. A failed or revoked read cannot produce a partially saved snapshot.

Steps use the provider's daily aggregate, which applies Health Connect's deduplication and source-priority rules. No hard-coded source package filter excludes phone or wearable data, including newer device-specific step origins. An absent aggregate is an unknown reading; it is not turned into an invented zero. A provider-returned zero remains a valid reading.

The dashboard shows actual daily steps in an interactive 7/30-day bar chart with date selection, observed-day total/average/best values and a sync timestamp. Unknown dates are gaps; a measured zero has a distinct mark.

`AppData.healthConnect` stores `HealthConnectSyncState`: the last successful sync time, its date range, granted categories at that sync, weight-reading count and `dailySteps`. It is part of complete backups and survives process recreation. It records a dated successful snapshot, not a promise that access is still granted now.

## Foreground refresh

After an explicit successful connection, `HealthConnectForegroundSync` refreshes once on every foreground visit, even if the last successful sync was seconds ago. Pause cancels an in-flight read and resets the visit gate; resume can retry. Recomposition cannot launch another read in the same visit. It never opens a permission prompt, schedules a background worker or writes records to the health provider.

Opening the explicit Health Connect panel suppresses the foreground controller, so it cannot compete with the panel's sync. Leaving the app cancels an in-flight read; returning can retry that interrupted attempt. Revocation or a provider error keeps the last successful data and timestamp unchanged. The panel's explicit Sync action provides visible recovery.

## Privacy

Health readings stay in Open Factor's device-local store and backups. They are not sent to the exercise video service. The application has Internet access for exercise demonstrations; the older statement that Open Factor has no Internet permission is obsolete. Revoking Health Connect access does not delete records already imported into Open Factor, and Open Factor never deletes the provider's records.

The earlier export helpers remain pure compatibility code, with unit coverage, but the current Health Connect surface provides the requested read-only connection instead of four separate transfer buttons.

## Verification

Unit coverage checks partial grants, paged scale reads, aggregate step requests without origin filtering, missing versus zero readings, live revocation, failed combined reads, manual-data precedence, stable IDs, backup round trips and malformed snapshots. Foreground gate tests cover initial consent, panel exclusion, repeated recomposition, recent-snapshot refresh on a new visit and cancellation/retry boundaries.

`HealthConnectProviderTest.oneButtonUiImportMatchesExternalProviderAndSurvivesReload` is an opt-in integration test. It requires a separate fixture app or the official Health Connect Toolbox to write known records to the **real Android Health Connect provider**, then the normal Open Factor Connect button and Android permission UI to import them. The test first asserts that this visible one-button flow already saved the external-origin reading and daily steps. It then reads the actual provider again and verifies idempotent persistence through a new store instance. It does not inject an app-local success result or present synthetic fixture records as a person's real health history.

Root's isolated `healthFixture` module is not a production app dependency. With the app and test APKs installed, an example targeted runner invocation is:

```sh
adb shell am instrument -w \
  -e class org.openfactor.app.HealthConnectProviderTest \
  -e healthProviderFixture true \
  -e healthFixtureOrigin org.openfactor.healthfixture \
  -e healthFixtureDate YYYY-MM-DD \
  -e healthFixtureWeightKg 72.6 \
  -e healthFixtureBodyFat 19.0 \
  -e healthFixtureSteps 4321 \
  org.openfactor.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use the device's fixture date in place of `YYYY-MM-DD`, and complete native consent plus the one-button import first. Normal source CI skips this opt-in test when no provider fixture is supplied. Unit tests or an unavailable-provider state alone are not evidence that a real provider transfer succeeded. Final release evidence must state which provider and device configuration were actually exercised.

Implementation follows the official [Health Connect setup and permissions guide](https://developer.android.com/health-and-fitness/health-connect/get-started), [aggregate-data guide](https://developer.android.com/health-and-fitness/health-connect/aggregate-data), and [Health Connect Toolbox testing guide](https://developer.android.com/health-and-fitness/health-connect/test/health-connect-toolbox).
