# Open Factor

An open source Android workout tracker built in a GPLv3 fork of [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile). Open Factor takes its floating navigation and grouped settings from Nuvio. Workout screens and behavior are being reconstructed from an explicitly supplied reference application.

**Development status:** a working training foundation, not yet a complete one-to-one replacement. Original exercise-video acquisition is deferred to a future phone-assisted pass; automated programming, body metrics and complete visual/interaction parity remain unfinished. Gym profiles and Levels have partial local implementations. See [the investigation ledger](docs/open-factor/INVESTIGATION.md).

## Build

Open Factor is the independent Android project in `openfactor/`. It does not need Nuvio's media engines or service keys. Use JDK 17 and Android SDK 35.

```sh
cd openfactor
# Set ANDROID_HOME to your SDK, or add sdk.dir to local.properties.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is `openfactor/app/build/outputs/apk/debug/app-debug.apk`, package `org.openfactor.app`. It can coexist with Nuvio and the reference app.

## Current training features

- Search exercises and aliases; filter muscles/equipment; favorite exercises.
- Camera-view photos, optional locally imported videos, available instructions, muscles and properties.
- Gym profiles, literal equipment preset prefills, weight lists and gym-aware exercise filtering.
- Local muscle set levels over week/month/three-month windows.
- Create custom exercises and reusable workout templates.
- Build manual programs with ordered rest/workout days, gym selection, seven-cycle set targets, library saving and activation.
- Launch a selected program day/cycle with rep/RIR targets and per-exercise rest overrides.
- Persist active workouts, selected exercise, pause/resume, weight, reps, RIR, warm-ups and notes.
- Automatic rest timer and exercise auto-next, pause-excluding summaries, completion, history and repeat workout.
- Kilograms/pounds with canonical kilogram storage.
- Export/restore local JSON backups, including programs and drafts.
- Nuvio-derived floating pill navigation and grouped settings.

## Local reference content

The public source includes descriptive metadata for all 1,345 exercises and 297 equipment entities, plus Material icons. Original instructions, images, fonts, animations and videos use an ignored local pack; their ownership is separate from this repository's GPL code license.

To create that pack from your own APK:

```sh
python tooling/import_reference_apk.py /absolute/path/to/Workouts.apk
cd openfactor
./gradlew :app:assembleDebug
```

For the inspected specimen, the importer produces 1,345 catalog entries, exact images for 619 exercises, and instructions for 1,213. It copies equipment and muscle assets, preserves the full source metadata, and records SHA-256 provenance. **No source videos have been retrieved or verified.** Static analysis resolves 1,831 object paths, but server access requires valid App Check. For matching local MP4 exports, add `--video-directory /path/to/export`; missing clips show photo previews.

The deferred phone-assisted media task is recorded in [TODO.md](docs/open-factor/TODO.md).

The importer never fetches remote content, executes the APK, or publishes imported files. Do not describe imported proprietary content as GPL-licensed. See [asset provenance](docs/open-factor/ASSETS.md).

## Upstream and license

Code is [GPLv3](LICENSE), preserving Nuvio's license and attribution. The original media implementation remains in `composeApp/`, `androidApp/`, and `iosApp/`; [the original upstream README](docs/upstream/NUVIO-README.md) documents those targets. Open Factor's Android entry point is `openfactor/app/`.
