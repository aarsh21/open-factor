# Validation record

Development checkpoint, 21 October 2026. This record validates the current training foundation, not full reference-app parity.

## Build and static checks

- Debug Android APK builds with JDK 17, Gradle 8.11.1, AGP 8.9.2 and Android SDK 35.
- Nine unit tests pass: completed working-set volume, kg/lb conversion and input bounds, full backup round-trip, rejection of unsupported backup versions, rest deadline/dashboard preference persistence, primary/secondary muscle set contributions, gym profile/weight backup compatibility, equipment alternative-group requirements, and direct equipment references versus group references.
- Android lint passes without errors. Remaining warnings concern dependency versions, convenience API suggestions and backup configuration.
- Local APK import yields 1,345 unique exercise IDs, 619 exact image joins and 1,213 instruction joins. It handles nullable bodyweight values, preserves the source catalog, and does not fabricate video URLs.

## Device checks

Connected phone: CPH2745, Android 17. Two instrumented tests pass: persistent workout data through a new storage instance, and opening every referenced local exercise image/instruction asset. Initial Compose/Espresso-driven tests failed before application assertions due to test-framework compatibility; the retained instrumentation tests use AndroidJUnit4 for device storage/media, and the UI flow uses explicit ADB actions.

Disposable read-only Pixel 7 emulator: Android 15, 1080×2400, density 420. `tooling/android_smoke.py` verifies starting a session, searching/adding an exercise, completing a set, force-stopping/restarting the application, resuming the saved session with its rest countdown, finishing it and reading the persisted history. It also captures the dashboard, logger, completion summary, exercise list and settings as native screenshots. The emulator's workouts are generated test data.

The gym UI was also checked on the emulator: create a blank profile, select Barbell, edit its weights to synthetic values, restart the process and verify the profile, equipment ID and weight list in persistent storage.

Raw screenshots and execution logs remain local. The UI smoke runner deliberately requires an `emulator-*` serial and does not clear application data. It leaves its generated test workout in that disposable instance.

## Reproduce

```sh
cd openfactor
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
cd ..
python tooling/android_smoke.py --serial emulator-5580 --output /tmp/open-factor-review
```

## Unverified requirements

Reference-app streamed videos, source program algorithms and complete visual/interaction parity remain unverified. See the investigation matrix for the outstanding features. No complete-clone acceptance has been recorded.
