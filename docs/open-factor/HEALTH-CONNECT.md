# Optional device-local Health Connect

Health Connect transfers require an explicit action in Settings or onboarding.
Local workouts, body records, photos, charts, and backups do not depend on a
provider, health permissions, an account, or Internet access. Opening the panel
checks provider availability but does not read or write health records.

## Recovered directions

The retained MacroFactor Workouts 1.4.1 (130) configuration establishes these
directions. Generic bridge support for other record types does not establish
that the workout app enables them.

| Category | Read from Health Connect | Write to Health Connect |
| --- | --- | --- |
| Weight | Yes | Yes |
| Scale body fat | Yes | Yes |
| Steps | Yes | No |
| Exercise sessions | No | Yes |

The Android manifest declares exactly those six health permissions. It does not
request nutrition, sleep, exercise-route, background-read, or history-read
permissions. Circumferences and visual body fat are not transferred. There is
no Internet permission, periodic worker, account integration, or automatic sync.

Source evidence consists of the indexed chapter 12 tour, native metadata, and
bounded static traces of the retained binary. The ARM64 `libapp.so` SHA-256 is
`870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`.
The permission map starts at `0x7a066c`, with direction assignments at
`0x7a0728` through `0x7a07fc`. Private traces remain excluded from Git.
Source permission grants and completed source transfers were not executed.

## Calendar dates and scale imports

The source derives calendar fields from the local date and reconstructs that
label as UTC midnight. This normalization does not mean that a raw health-record
instant belongs to its UTC calendar date. Local export reconstructs local
midnight and converts that instant for the native bridge.

The source read normalization is at `0x83216c`. The local-date reconstruction is
at `0xb578a8`, and scale export reconstruction is at `0x8e642c`. The native
epoch bridge at `0x1027984` selects local time. The implementation therefore
uses the device-local calendar date on import and local midnight with its
actual zone offset on export. It does not invent noon or migrate saved dates.

Weight and body fat independently select the latest timestamp on each date.
Equal timestamps retain the first sample. Only dates with weight produce a
scale record. Body fat rounds to one decimal place. Source selectors are at
`0x1028efc` and `0x1029238`, with scale composition at `0x10296c4`.

Source merge priority puts manual data ahead of Health Connect. The integration
priority dispatch resolves to `0x107f3a8`, the internal-source priority to
`0x107f3dc`, and the rejection comparison to `0x831400`.

Open Factor adds the optional `ScaleWeightRecord.healthConnectOrigin` marker.
This is a local provenance policy, not recovered source serialization:

- Own-package records are excluded from incoming scale reads.
- Any existing manual record preserves its date against an import.
- A repeated import can update an imported date while retaining its local ID.
- Fresh dates receive independent UUIDs. Moving a manually edited import does
  not collide with a later import on its former date.
- Manual Save reconstructs the record without provenance.
- Complete backups, selected scale exports, and charts retain imported records.
  Only outbound Health Connect exports omit imported records to avoid feedback.

Both paged reads must finish before the caller receives any local records.
Revocation or failure during the body-fat read cannot produce a partial local
weight import. The parent-owned view model saves the merge durably.

## Finished-workout mapping

The source mapper at `0x10c48f0` uses the larger of the elapsed duration and
eligible-set count multiplied by 10 seconds. It creates sequential 10-second
`OTHER_WORKOUT` segments across exercises and assigns each segment's repetitions.
The comparison at `0x10c496c` is MAX, not MIN. There is no requirement to recover
per-set clocks or add timestamps to existing records.

Source eligibility excludes warmups, skipped logs, and empty logs. The warmup
filter is at `0x7bd368`, the skipped/empty filter at `0x7bd45c`, and serialized
`isSkipped` at `0x7ae2a0`. RIR and rest alone do not make a log nonempty.

Open Factor's logger has independent side and Myo component logs, but no source
skipped or partial-rep model. Its local policy exports one segment for each
non-warmup logical set with a completed log that has a positive performed metric.
Segment repetitions sum those eligible logs, including distinct sides and Myo
components. This grouping and repetition policy is a local adaptation, not
source equivalence. Elapsed time subtracts saved pauses. A finished positive-
duration session with no eligible sets has no segments. A zero-duration,
zero-set session is omitted because Android requires the end after the start.

## Explicit actions and idempotent writes

The panel uses the last 30 local calendar days, including today. This bounded
window is a local policy, not a recovered source sync range. Daily steps use
Health Connect's period aggregation, which applies provider source-priority
deduplication. Step totals remain in the panel and are not saved in backups.

Each action checks provider status and its current grants. Missing or partial
grants stop the action. Permission review requires a separate confirmation.
Returning from Android's permission screen never starts a transfer, including
after full approval. The user must tap the action again. Empty or invalid
exports stop before permission review. A provider removed before review cannot
crash the permission launcher.

The source native overwrite path deletes a range before insertion. Open Factor
instead uses stable `open-factor:{weight|fat|workout}:<localID>` client record
IDs and a single insert of at most 1,000 records. This is an approved local
policy. No Health Connect deletion API is called. Deleting a local record or
clearing an optional body-fat field does not remove a prior exported record.

Export versions use the private `health_connect` preferences, outside AppData
and user backups. The next version is the greater of the previous version plus
one and the nonnegative device clock. A process-wide lock protects synchronous
persistence before insertion. Persistence failure or counter exhaustion stops
the export. Restart and clock rollback preserve increasing versions within the
same installation. Reinstall or app-data clearing loses that guarantee against
records already in Health Connect. The export does not claim otherwise.

Denial leaves local use unchanged. Revocation during an operation reports no
confirmed completion. Provider or transport failure is not presented as a
successful transfer. A successful permission response is not a transfer result.

## Android API contract

The integration uses stable `androidx.health.connect:connect-client:1.1.0`.
The [Android setup guide](https://developer.android.com/health-and-fitness/health-connect/get-started)
requires compile SDK 36 and Android Gradle plugin 8.9.1 or later for this
version. Open Factor keeps target SDK 35 and minimum SDK 26. The client supports
API 26, but provider availability requires API 28 or later. Android 14 includes
Health Connect in the platform. Earlier supported versions use the provider app.

The manifest has the provider-package visibility query, an exported rationale
activity for Android 13 and earlier, and the protected permission-usage alias
for Android 14 and later. The alias uses `START_VIEW_PERMISSION_USAGE` and the
`HEALTH_PERMISSIONS` category as documented by Android.

The AndroidX 1.1.0 `HealthConnectClient.insertRecords` contract specifies an
atomic batch and deduplication by client record ID, with the higher client
record version taking precedence. Its `Metadata.manualEntry` factory supplies
that metadata. These guarantees come from the
[AndroidX API](https://developer.android.com/reference/kotlin/androidx/health/connect/client/HealthConnectClient)
and [Metadata reference](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Metadata),
not from executing the provider. No real provider behavior is certified here.

## Verification limits

Eight Health-specific JVM tests cover asymmetric local/UTC dates, independent
latest samples and timestamp ties, own-origin exclusion, manual priority,
repeat-import IDs, complete and scoped backup retention, chart retention, DST
midnight exports, stable client IDs, workout filters and short/empty sessions,
counter rollback and overflow, missing providers, partial grants, revocation,
export-producer failure, cancellation, and all-or-nothing paged imports.
They use an in-process fake provider, never a health store. The provenance
dependency also extends the body-record backup regression.

The first integrated build stopped at AAR metadata validation because compile
SDK 35 was too old. No tests ran in that attempt. The corrected integrated
overlay with compile SDK 36 passes 88 maintained JVM tests, including all eight
Health tests, lint with zero errors and 11 warnings, and both APK assemblies.
The overlay includes the parent's durable import and Settings/onboarding
wiring, provenance, and corrected scoped exports. The merged manifest has
exactly the six health permissions and no Internet permission. The public APK
contains only the three factual JSON assets, with no private source originals.

Native UI-only checks pass on an explicitly leased offline emulator at normal
and 1.3 text scales. Settings and onboarding open the panel and return to their
callers without a transfer. Onboarding stays on the same step. The direct
privacy rationale opens, and real scrolling exposes the complete final
paragraph. Inspected final captures show readable controls, complete footer
text, and no caller content behind the gesture bar. The first render exposed
that last defect. The corrected full-screen dialog draws behind system bars
while `safeDrawingPadding` keeps its content clear of them.

The final public APK SHA-256 is
`189a0948553cf7ffdf696b75a53389fbbef59e0b279772b2dec3bc900c389b2f`.
Its final build repeats the 88-test, zero-lint-error, 11-warning result and both
APK assemblies. The incoming APK, test APK, exact preference bytes, private
user-file hashes, empty profile marker, font, rotation, and offline settings
are restored after verification. The crash buffer is empty.

Android permission denial, unavailable-provider renders, provider transfers,
persistence across a real process restart, and provider idempotence remain
unverified. No health grant or health-store operation has been executed.
The private counter's synchronous commit is intentional despite lint's KTX
suggestion.

`tooling/android_health_connect_smoke.py` prepares Settings, onboarding, and
rationale checks at normal and 1.3 text scales on an explicitly leased offline
emulator. It pins the installed APK hash, saves a complete private-app archive,
restores preferences byte-for-byte, and compares private-file hashes. Its
`--expect-provider` option asserts the actual environment state rather than
forcing an unavailable provider. It never taps transfer or permission controls.
The driver therefore does not claim native grant-denial coverage. That boundary
is tested by the no-store fake until a synthetic health environment is allocated.
Scroll gestures use empty edge padding rather than transfer buttons. Footer
captures wait for real scrolling to stop, not the first visible piece of a
paragraph. Runtime artifacts and complete backups remain under the excluded
`.amp/in/artifacts/` directory, never in public application assets.
