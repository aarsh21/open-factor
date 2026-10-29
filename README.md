# Open Factor

An independently maintained Android workout app with straightforward logging and original exercise guidance. Choose an exercise, view its demonstration and muscles, record weight and reps, add warmups, and save your workout. Open Factor uses [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) as its base, retaining its floating navigation and grouped settings.

“Simple” means useful training features in an uncluttered interface. The 0.4.0 checkout adds real Health Connect imports, stronger exercise search, quick weight logging, recorded training graphs and muscle workload to the original exercise guidance. Photos, instructions, anatomy and equipment artwork use an efficient static pack; original videos play on demand over HTTPS. Core logging and bundled guidance work offline without an account. Videos require a bundled clip or available streamed/cached data. See [media packaging and playback](docs/open-factor/MEDIA-PACKAGING.md).

[Download Open Factor 0.4.0](https://github.com/aarsh21/open-factor/releases/tag/v0.4.0). The release includes the signed original-media APK, checksums and a verification bundle. See the [release notes and exact verification scope](docs/open-factor/releases/v0.4.0.md).

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

- Quick unit setup, direct workout start, compact exercise selection, and visible Finish, Warmup and Add set actions.
- Search all 1,345 catalog exercises with ranked names, aliases, abbreviations, reordered terms and bounded typo matching; filter muscles/equipment or explicitly choose All equipment; favorite exercises. Original video demonstrations cover 619 exercises.
- Original camera-view photos and instructions where recovered. Library selection opens Guide, Muscles and History; exercise settings are a secondary action.
- A compact photo/name selector switches exercises within the workout. Guide opens guidance above the active workout; dismissing it preserves the selected exercise and set values.
- Original male/female anatomy with selectable primary and secondary muscles, plus equipment illustrations and exercise properties where available.
- Frameless portrait video with explicit Play, automatic looping through available camera angles, direct camera selection, 1X/2.5X/5X speed, pause/replay and a bounded 128 MiB disk cache. Opening a guide does not start a video download.
- Gym profiles, literal equipment preset prefills, weight lists and gym-aware exercise filtering.
- Custom exercises, reusable templates, and manual programs with rest/workout days, gym selection and variable-cycle targets. Programs remain accessible from Workout.
- Program-day launches with rep/RIR targets and per-exercise rest overrides; weekly recorded training graphs, front/back anatomy and direct/indirect muscle-set counts over week/30-day/90-day windows.
- Persist active workouts, selected exercise, pause/resume, weight, reps, optional RIR, warmups and notes.
- Optional workout/rest notifications. Precise background timing requires Android special access; force-stop suppresses reminders until reopening.
- Automatic rest timing, pause-excluding summaries, history and repeat workout; kilograms/pounds with canonical kilogram storage.
- Local body data and progress photos, plus export/restore backups including programs and drafts.
- Plus shortcuts include Log Weight in a bottom sheet that preserves the current destination and active workout. Weight charts show daily readings and a labeled trailing seven-day mean without treating missing days as zero.
- One Health Connect action requests read permissions and imports real weight, body fat and daily step aggregates. Previously connected data refreshes when the foreground app becomes stale; the app never writes to the provider. See [Health Connect behavior and provider verification](docs/open-factor/HEALTH-CONNECT.md).

See [manual program usage and limits](docs/open-factor/PROGRAMS.md), [training analytics](docs/open-factor/LIVE_DATA.md) and [body charts](docs/open-factor/BODY-CHARTS.md). Test commands above describe how to run checks; completed release checks belong to their versioned evidence record and do not establish universal device coverage.

## Original media and source-only fallback

The recovered collection contains **1,831 original MP4s for 619 exercises**, **1,832 exercise photos**, **44 muscle SVGs**, **four body assets**, **297 equipment images**, and 1,213 instruction files, of which 1,212 contain text. These are asset counts, not complete guidance coverage for the 1,345-exercise catalog.

Static staging preserves exercise-photo, anatomy and instruction bytes, derives equipment previews at up to 256 × 256 pixels, and leaves imported originals unchanged. The current static manifest records **32,986,897 bytes** before APK compression, with no bundled MP4s. Video objects keep their original exercise/view identities and are requested through HTTPS only after Play. The 128 MiB least-recently-used cache can evict old bytes; it is not a permanent offline video library. The optional `full` mode bundles the roughly 2.73 GB original video payload.

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

Code remains [GPLv3](LICENSE), preserving Nuvio's license, copyright notices and attribution. Media licenses are separate. The upstream media application remains in `composeApp/`, `androidApp/`, and `iosApp/`; [the original upstream README](docs/upstream/NUVIO-README.md) documents those targets. Open Factor's Android entry point is `openfactor/app/`.
