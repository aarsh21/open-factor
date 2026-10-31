# Open Factor

An independently maintained Android workout app with straightforward logging and original exercise guidance. Choose an exercise, view its demonstration and muscles, record weight and reps, add warmups, and save your workout. Open Factor uses [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) as its base, retaining its floating navigation and grouped settings.

“Simple” means useful training features in an uncluttered interface. The 0.7.0 app includes unified exercise search, a photo/progress exercise navigator, reusable templates, a dated workout history, pose-first camera logging, and interactive step charts with Health Connect refresh on every foreground visit. The dashboard shows three recorded-training circles and muscle workload; full-width portrait videos retain their side-mounted angle previews and floating speed controls. Workout summaries and exercise history use full-width set tables; saved workouts support isolated draft editing. Log all sets validates pending values, with an optional last-set effort rating after exercise completion. Dashboard has calendar Week/Month views, and all target muscles are visible together. Photos, instructions, anatomy and equipment artwork use an efficient static pack; original videos autoplay in visible exercise guides over HTTPS. Core logging and bundled guidance work offline without an account. Videos require a bundled clip or available streamed/cached data. See [media packaging and playback](docs/open-factor/MEDIA-PACKAGING.md).

[**Download the latest signed APK**](https://github.com/aarsh21/open-factor/releases/latest/download/open-factor.apk) · [Open Factor 0.8.1 release page](https://github.com/aarsh21/open-factor/releases/tag/v0.8.1). Signed original-media APKs, checksums and verification receipts are attached to GitHub Releases. See the [release notes and exact verification scope](docs/open-factor/releases/v0.8.1.md).

Successful versioned merges to `main` publish signed APKs to GitHub Releases after the complete Android CI workflow passes. See [release automation and versioning](docs/open-factor/RELEASING.md).

## Build and test on Android

Open Factor is the independent Android project in `openfactor/`. Use JDK 17 and Android SDK Platform 36. The app targets API 35 and supports Android 8.0 or newer. Static/full media staging additionally needs Python 3.11+ and ImageMagick's `magick`; source-only staging needs Python.

```sh
cd openfactor
# Set ANDROID_HOME to your SDK, or add sdk.dir to local.properties.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The default `auto` media mode includes the original static pack when a local `reference/catalog.json` is present. Otherwise it builds the source-only fallback. Source-only builds contain descriptive exercise/equipment data, bundled DM Sans and the limited licensed illustration pack; they do not contain recovered photos, anatomy, instructions or video identities. They are distinct from the intended original-media experience.

```sh
# Explicit source-only build, even with a reference pack on this machine.
./gradlew :app:assembleDebug -PopenfactorMediaMode=source

# Require the original static pack; keep videos outside the APK.
./gradlew :app:assembleDebug -PopenfactorMediaMode=static

# Optional: point the media client at your own HTTPS service.
./gradlew :app:assembleDebug -PopenfactorMediaMode=static \
  -PexerciseMediaBaseUrl=https://media.example.com/
```

The debug APK is `openfactor/app/build/outputs/apk/debug/app-debug.apk`, package `org.openfactor.app`. It can coexist with Nuvio and the reference app. Service configuration does not establish that a server is deployed or its videos are uploaded. The [Hono/Node and private RustFS service](backend/media/README.md) includes Dokploy configuration and an authenticated uploader; no account service is required for workout logging.

`tooling/build_public_release.sh` explicitly builds the source-only variant, signs it using externally supplied persistent credentials, and writes certificate and SHA-256 receipts. Its public APK verifier checks the source-only asset allowlist, preview hashes, catalog, ZIP integrity and package size. That verifier is not acceptance for a reference-containing artifact. Never commit a keystore or password.

`tooling/build_media_release.sh` builds and signs the intended original-static-media variant using the same external credentials. `tooling/verify_media_apk.py` checks the packaged file manifest, original hashes, media counts and exact video identities against the backend index. Pass `--reference /path/to/reference` to compare the APK against untouched imported source bytes as well. Signature and deployed-video checks are separate receipts.

## Training and exercise guidance

- Brief onboarding entrance and direction-aware step transitions, touch feedback on primary actions, and set-completion confirmation. Android Remove animations is respected live; restored setup and completed sets do not replay entrances.
- Three-step personal setup with name, optional gender/weight/height, units, direct Health Connect import that returns to setup after saving, weekly workout goal and training preferences. Skip/resume retains the draft; Profile supports later editing. Direct workout start, compact exercise selection, and visible Finish, Warmup and Add set actions.
- Search all 1,345 catalog exercises with ranked names, aliases, abbreviations, reordered terms and bounded typo matching; filter muscles/equipment or explicitly choose All equipment; favorite exercises. Original video demonstrations cover 619 exercises.
- Original camera-view photos and instructions where recovered. Compact previews fill their rounded frames without letterbox gutters throughout the library, workouts, templates, programs and history; full guides retain their natural aspect. Missing images have an accessible neutral fallback. Library selection opens Guide, Muscles and History; exercise settings are a secondary action.
- A photo navigator shows every exercise in the workout with a set-progress ring, done check and superset grouping; one tap switches, and a Next exercise action appears under a finished exercise. Guide opens guidance above the active workout; dismissing it preserves the selected exercise and set values.
- Swap exercise… in the exercise menu suggests same-muscle alternatives that fit the workout's gym (favorites and recent exercises first among equals), each with its guide, plus Search all. Completed sets stay with the original; remaining sets, targets, set kinds, sides and rest move to the replacement without carrying weights. Template and program-day workouts ask Just today or Also update; Undo restores the exact previous workout and plan.
- Original male/female anatomy with selectable primary and secondary muscles, plus equipment illustrations and exercise properties where available.
- Frameless portrait video autoplays muted while visible, loops through available camera angles, and supports direct camera selection, 1X/2.5X/5X speed, pause/replay and a bounded 128 MiB disk cache. Background/offscreen playback releases its player; deliberate pause is preserved.
- Gym profiles, literal equipment preset prefills, weight lists and gym-aware exercise filtering.
- Four editable starter splits: Upper/Lower/Push/Pull/Legs, Upper/Lower, Bro split and Full body. Preview all seven days including rest days, then copy the split into your templates. Edit exercise order, sets, rep targets and rest; choose exercises using the shared search and filters. Starting a day creates blank actual weight/reps with its targets shown separately.
- Custom exercises, reusable templates, and manual programs with rest/workout days, gym selection and variable-cycle targets. Programs remain accessible from Workout.
- Settings → Import spreadsheet previews the supplied Outlift-style Phase 1/2/3 XLSX layout and appends inactive weekly programs after explicit set-count, exercise-match and omission review. It does not fabricate training history or evaluate formulas. See [supported layout and import limitations](docs/open-factor/SPREADSHEET-IMPORT.md); this is not a lossless/general-purpose spreadsheet importer.
- Program-day launches with rep/RIR targets and per-exercise rest overrides; weekly recorded training graphs, front/back anatomy and direct/indirect muscle-set counts over week/30-day/90-day windows.
- Persist active workouts, selected exercise, pause/resume, weight, reps, optional RIR, warmups and notes.
- A compact in-app workout keypad replaces the system keyboard for set values: digits, unit-aware −/+ steps with hold-to-repeat, Next across fields and unfinished sets, and Log set. It keeps the focused field visible and can be turned off in Workout settings.
- Exercise notes at three levels: a pinned note shows every time, a template note shows only in workouts started from that template (edit it with the template targets or from the workout's exercise menu), and a muted, dated "Last time" note previews your latest saved note until you write today's. It is never copied; swipe or tap close to hide it for that workout.
- Optional workout/rest notifications. Precise background timing requires Android special access; force-stop suppresses reminders until reopening.
- Automatic rest timing, pause-excluding summaries, history and repeat workout; kilograms/pounds with canonical kilogram storage.
- Local body data and progress photos, plus export/restore backups including programs and drafts.
- Plus shortcuts include Log Weight in a bottom sheet that preserves the current destination and active workout. Weight charts show daily readings and a labeled trailing seven-day mean without treating missing days as zero.
- One Health Connect action requests read permissions and imports real weight, body fat and daily step aggregates. Previously connected data refreshes when the foreground app becomes stale; the app never writes to the provider. See [Health Connect behavior and provider verification](docs/open-factor/HEALTH-CONNECT.md).

See [manual program usage and limits](docs/open-factor/PROGRAMS.md), [training analytics](docs/open-factor/LIVE_DATA.md) and [body charts](docs/open-factor/BODY-CHARTS.md). Test commands above describe how to run checks; completed release checks belong to their versioned evidence record and do not establish universal device coverage.

## Original media and source-only fallback

The recovered collection contains **1,831 original MP4s for 619 exercises**, **1,832 exercise photos**, **44 muscle SVGs**, **four body assets**, **297 equipment images**, and 1,213 instruction files, of which 1,212 contain text. These are asset counts, not complete guidance coverage for the 1,345-exercise catalog.

Static staging preserves exercise-photo, anatomy and instruction bytes, derives equipment previews at up to 256 × 256 pixels, and leaves imported originals unchanged. The current static manifest records **32,986,897 bytes** before APK compression, with no bundled MP4s. Video objects keep their original exercise/view identities and are requested through HTTPS when their visible guide autoplays or the user explicitly retries playback. The 128 MiB least-recently-used cache can evict old bytes; it is not a permanent offline video library. The optional `full` mode bundles the roughly 2.73 GB original video payload.

The repository keeps recovered content outside Git. To import an operator-supplied APK and matching video export:

```sh
python tooling/import_reference_apk.py /absolute/path/to/Workouts.apk \
  --video-directory /absolute/path/to/mac-media-export
```

Then use the default/static build above, or explicitly select `-PopenfactorMediaMode=full` for a large local offline-video APK. The legacy `-PincludePrivateReference=false` selects source mode; `true` selects full mode. See [packaging modes](docs/open-factor/MEDIA-PACKAGING.md), [asset provenance](docs/open-factor/ASSETS.md), and the [original-media return receipt](docs/open-factor/media-handoff/RECEIPT.md).

Source-only builds retain [44 attributed Everkinetic illustrations covering 21 reviewed variants](docs/open-factor/LICENSED-MEDIA.md), DM Sans under OFL, Material icons and descriptive catalog metadata. Missing original media uses the available preview/fallback or an explicit unavailable state. Recovered media retains its original ownership; neither the code's GPL license nor local import grants redistribution rights.

## Earlier release 0.2.0

[Open Factor 0.2.0](https://github.com/aarsh21/open-factor/releases/tag/v0.2.0) is the earlier source-only APK with the limited licensed preview collection and no original demonstration videos. Its [release notes and verification scope](docs/open-factor/releases/v0.2.0.md) remain the record for that artifact, not the restored-media 0.3.0 work.

**Upgrading from a testing alpha to 0.2.0:** that release introduced a dedicated signing key. Export Backup in the old app, keep the backup outside the app, uninstall the alpha, install 0.2.0, then Restore Backup. Android rejects an in-place update signed by a different key. Uninstalling without a backup deletes local training and photos.

## Credits and license

Thanks to **[NuvioMedia and the NuvioMobile contributors](https://github.com/NuvioMedia/NuvioMobile)** for the base application and the navigation/settings foundation. See [CREDITS.md](CREDITS.md).

Code remains [GPLv3](LICENSE), preserving Nuvio's license, copyright notices and attribution. Media licenses are separate. The unused upstream media-player targets and their release pipelines have been removed; [the original upstream README](docs/upstream/NUVIO-README.md) is retained as historical attribution, not current build instructions. Open Factor's Android entry point is `openfactor/app/`.

## Performance and test data

Dashboard customization uses cached options and background exercise search. Weekly summaries aggregate dates once; seven-day weight means use a sliding window. Unchanged workout history is stored in a separate atomic archive, so logger edits rewrite only a small live snapshot. Full exported backups remain self-contained and old inline storage migrates on the next save. See [performance checks, synthetic datasets, and storage details](docs/open-factor/PERFORMANCE.md). No universal device/frame-time claim is implied.
