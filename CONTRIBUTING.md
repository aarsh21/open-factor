# Contributing to Open Factor

Open Factor is a native Android workout app. Its application and Gradle project live in `openfactor/`; the optional exercise-media service lives in `backend/media/`.

## Before changing code

- Read [PRODUCT.md](PRODUCT.md), [DESIGN.md](DESIGN.md), and the relevant feature documentation in `docs/open-factor/`.
- Keep changes focused. Explain the problem, behavior changes, and known limitations.
- Preserve users' saved training and backup compatibility. Never replace real data with synthetic records without explicit consent.
- Keep imported reference media, signing credentials, private captures, and generated build output out of Git.
- Preserve the license and the attribution in [CREDITS.md](CREDITS.md). Code is MIT; media licenses are separate.

## Build and test

Use JDK 17 and Android SDK Platform 36. From the repository root:

```sh
./openfactor/gradlew -p openfactor :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PopenfactorMediaMode=source
./openfactor/gradlew -p openfactor :app:connectedDebugAndroidTest -PopenfactorMediaMode=source
python3 -m unittest discover -s tooling -p 'test_*.py'
```

Source mode is reproducible without private media. Static-media packaging has separate checks described in [media packaging](docs/open-factor/MEDIA-PACKAGING.md). Backend commands are documented in [backend/media/README.md](backend/media/README.md).

Test data generation and performance checks are documented in [PERFORMANCE.md](docs/open-factor/PERFORMANCE.md). Use a disposable emulator; backup restoration replaces the destination's data.

## Pull requests and reports

Describe the change and tests actually run, including failures or skipped checks. UI changes should include native screenshots or recordings and enlarged-text checks. Performance changes should state dataset size, device/build conditions, and measured scope rather than claim universal smoothness. Storage changes need migration, restart, backup, and recovery checks.

Bug reports should include app version, Android/device version, reproduction steps, expected/actual behavior, and relevant logs with personal data removed. Discuss large product or architecture changes before submitting them.
