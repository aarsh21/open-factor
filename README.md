# Open Factor

An independently maintained, offline Android workout logger. Choose an exercise, enter weight and reps, add warmups, and save your workout. Open Factor uses [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) as its base, retaining its floating navigation and grouped settings.

The current direction prioritizes simple logging over reproducing another app. Core training works without an account or a network connection. Optional cloud accounts and video hosting are not included in this release.

## Test on Android

Download the installable APK from [Open Factor 0.2.0](https://github.com/aarsh21/open-factor/releases/tag/v0.2.0). Android 8.0 or newer is required. Read the [release notes and verification scope](docs/open-factor/releases/v0.2.0.md).

**Upgrading from a testing alpha:** 0.2.0 introduces a dedicated release signing key. Export Backup in the old app, keep the backup safely outside the app, uninstall the alpha, install 0.2.0, then Restore Backup. Android rejects an in-place update signed by a different key. Uninstalling without a backup deletes local training and photos.

## Build

Open Factor is the independent Android project in `openfactor/`. It does not need Nuvio's media engines or service keys. Use JDK 17 and Android SDK Platform 36. The app still targets API 35 and supports Android 8.0 or newer.

```sh
cd openfactor
# Set ANDROID_HOME to your SDK, or add sdk.dir to local.properties.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Public builds exclude the private reference pack even when it is present locally. `tooling/verify_public_apk.py` checks the shipped asset allowlist, preview hashes, catalog, ZIP integrity, and package size. `tooling/build_public_release.sh` creates an optimized release, signs it using externally supplied persistent credentials, and writes certificate and SHA-256 receipts. Never commit a keystore or password.

The debug APK is `openfactor/app/build/outputs/apk/debug/app-debug.apk`, package `org.openfactor.app`. It can coexist with Nuvio and the reference app.

## Current training features

- Search exercises and aliases; filter muscles/equipment; favorite exercises.
- Camera-view photos, optional locally imported videos, available instructions, muscles and properties.
- Gym profiles, literal equipment preset prefills, weight lists and gym-aware exercise filtering.
- Local muscle set levels over week/month/three-month windows.
- Create custom exercises and reusable workout templates.
- Build manual programs with ordered rest/workout days, gym selection, variable-cycle set targets, library saving and activation.
- Launch a selected program day/cycle with rep/RIR targets and per-exercise rest overrides.
- Quick setup, direct workout start, compact exercise selection, and visible Finish, Warmup and Add set actions.
- Persist active workouts, selected exercise, pause/resume, weight, reps, RIR, warm-ups and notes.
- Optional active-workout and background rest notifications. Precise timing requires Android special access; force-stop suppresses reminders until the app opens again.
- Automatic rest timer and exercise auto-next, pause-excluding summaries, completion, history and repeat workout.
- Kilograms/pounds with canonical kilogram storage.
- Export/restore local JSON backups, including programs and drafts.
- Nuvio-derived floating pill navigation and grouped settings.

See [manual program usage and limits](docs/open-factor/PROGRAMS.md).

## Local reference content

The public source includes descriptive metadata for 1,345 exercises and 297 equipment entities, Material icons, DM Sans, and [44 attributed exercise illustrations covering 21 reviewed variants](docs/open-factor/LICENSED-MEDIA.md). Other exercises use an icon fallback. The public release contains no demonstration videos. Media licenses are separate from this repository's GPL code license.

The following optional private import workflow is for local inspection and is excluded from public builds by default. Original instructions, images, fonts, animations and videos retain their original ownership.

To create that pack from your own APK:

```sh
python tooling/import_reference_apk.py /absolute/path/to/Workouts.apk
cd openfactor
./gradlew :app:assembleDebug
```

To explicitly include a local reference pack, pass `-PincludePrivateReference=true`. That artifact is for private use and fails the public-release asset verification.

For the inspected specimen, the importer produces 1,345 catalog entries, exact images for 619 exercises, and 1,213 instruction-file links. Of those files, 1,212 contain text and one is empty. It copies equipment and muscle assets, preserves the full source metadata, and records SHA-256 provenance.

The private Mac export supplies all 1,831 expected original MP4s. Add `--video-directory /path/to/mac-media-export` to import them into a new pack. The original video payload is 2.73 GB and produces a large local APK. Videos cover 619 exercises, not the full catalog; missing clips retain photo previews or an explicit unavailable state. The [return receipt](docs/open-factor/media-handoff/RECEIPT.md) records verification and the remaining media and instruction gaps. The published alpha.4 release predates this video import.

The importer never fetches remote content, executes the APK, or publishes imported files. Do not describe imported proprietary content as GPL-licensed. See [asset provenance](docs/open-factor/ASSETS.md).

## Credits and license

Thanks to **[NuvioMedia and the NuvioMobile contributors](https://github.com/NuvioMedia/NuvioMobile)** for the base application and the navigation/settings foundation. See [CREDITS.md](CREDITS.md).

Code remains [GPLv3](LICENSE), preserving Nuvio's license, copyright notices and attribution. The original media implementation remains in `composeApp/`, `androidApp/`, and `iosApp/`; [the original upstream README](docs/upstream/NUVIO-README.md) documents those targets. Open Factor's Android entry point is `openfactor/app/`.
