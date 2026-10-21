# Open Factor

An open source Android workout tracker built in a GPLv3 fork of [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile). Open Factor takes its floating navigation and grouped settings from Nuvio. Workout screens and behavior are being reconstructed from an explicitly supplied reference application.

**Development status:** a working training foundation, not yet a complete one-to-one replacement. Exercise video downloads, automated programming, gym profiles, body metrics, levels and several source interactions remain under investigation. See [the investigation ledger](docs/open-factor/INVESTIGATION.md).

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
- Exercise image demonstrations, available instructions, muscles and properties.
- Create custom exercises and reusable workout templates.
- Persist active workouts, weight, reps, RIR, warm-ups and notes.
- Automatic rest timer, workout completion, history and repeat workout.
- Kilograms/pounds with canonical kilogram storage.
- Export/restore local JSON backups.
- Nuvio-derived floating pill navigation and grouped settings.

## Local reference content

The public source builds with a small factual exercise-name catalog and Material icons. The complete reference catalog and imported media are kept in an ignored local pack; their ownership is separate from this repository's GPL code license.

To create that pack from your own APK:

```sh
python tooling/import_reference_apk.py /absolute/path/to/Workouts.apk
cd openfactor
./gradlew :app:assembleDebug
```

For the inspected specimen, the importer produces 1,345 catalog entries, exact images for 619 exercises, and instructions for 1,213. It copies equipment and muscle assets, preserves the full source metadata, and records SHA-256 provenance. **No videos are bundled or recovered.** Image cycling is a local demonstration fallback, not the original streamed video.

The importer never fetches remote content, executes the APK, or publishes imported files. Do not describe imported proprietary content as GPL-licensed. See [asset provenance](docs/open-factor/ASSETS.md).

## Upstream and license

Code is [GPLv3](LICENSE), preserving Nuvio's license and attribution. The original media implementation remains in `composeApp/`, `androidApp/`, and `iosApp/`; [the original upstream README](docs/upstream/NUVIO-README.md) documents those targets. Open Factor's Android entry point is `openfactor/app/`.
