# Building Open Factor

Technical notes for developers. For what the app does and how to install it, see the [README](../../README.md).

Successful versioned merges to `main` publish signed APKs to GitHub Releases after the complete Android CI workflow passes. See [release automation and versioning](RELEASING.md).

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

The debug APK is `openfactor/app/build/outputs/apk/debug/app-debug.apk`, package `org.openfactor.app`. It can coexist with the reference app. Service configuration does not establish that a server is deployed or its videos are uploaded. The [Hono/Node and private RustFS service](../../backend/media/README.md) includes Dokploy configuration and an authenticated uploader; no account service is required for workout logging.

`tooling/build_public_release.sh` explicitly builds the source-only variant, signs it using externally supplied persistent credentials, and writes certificate and SHA-256 receipts. Its public APK verifier checks the source-only asset allowlist, preview hashes, catalog, ZIP integrity and package size. That verifier is not acceptance for a reference-containing artifact. Never commit a keystore or password.

`tooling/build_media_release.sh` builds and signs the intended original-static-media variant using the same external credentials. `tooling/verify_media_apk.py` checks the packaged file manifest, original hashes, media counts and exact video identities against the backend index. Pass `--reference /path/to/reference` to compare the APK against untouched imported source bytes as well. Signature and deployed-video checks are separate receipts.

## Original media and source-only fallback

The recovered collection contains **1,831 original MP4s for 619 exercises**, **1,832 exercise photos**, **44 muscle SVGs**, **four body assets**, **297 equipment images**, and 1,213 instruction files, of which 1,212 contain text. These are asset counts, not complete guidance coverage for the 1,345-exercise catalog.

Static staging preserves exercise-photo, anatomy and instruction bytes, derives equipment previews at up to 256 × 256 pixels, and leaves imported originals unchanged. The current static manifest records **32,986,897 bytes** before APK compression, with no bundled MP4s. Video objects keep their original exercise/view identities and are requested through HTTPS when their visible guide autoplays or the user explicitly retries playback. The 128 MiB least-recently-used cache can evict old bytes; it is not a permanent offline video library. The optional `full` mode bundles the roughly 2.73 GB original video payload.

The repository keeps recovered content outside Git. To import an operator-supplied APK and matching video export:

```sh
python tooling/import_reference_apk.py /absolute/path/to/Workouts.apk \
  --video-directory /absolute/path/to/mac-media-export
```

Then use the default/static build above, or explicitly select `-PopenfactorMediaMode=full` for a large local offline-video APK. The legacy `-PincludePrivateReference=false` selects source mode; `true` selects full mode. See [packaging modes](MEDIA-PACKAGING.md), [asset provenance](ASSETS.md), and the [original-media return receipt](media-handoff/RECEIPT.md).

Source-only builds retain [44 attributed Everkinetic illustrations covering 21 reviewed variants](LICENSED-MEDIA.md), DM Sans under OFL, Material icons and descriptive catalog metadata. Missing original media uses the available preview/fallback or an explicit unavailable state. Recovered media retains its original ownership; neither the code's MIT license nor local import grants redistribution rights.

## Performance and test data

Dashboard customization uses cached options and background exercise search. Weekly summaries aggregate dates once; seven-day weight means use a sliding window. Unchanged workout history is stored in a separate atomic archive, so logger edits rewrite only a small live snapshot. Full exported backups remain self-contained and old inline storage migrates on the next save. See [performance checks, synthetic datasets, and storage details](PERFORMANCE.md). No universal device/frame-time claim is implied.

## Earlier release 0.2.0

[Open Factor 0.2.0](https://github.com/aarsh21/open-factor/releases/tag/v0.2.0) is the earlier source-only APK with the limited licensed preview collection and no original demonstration videos. Its [release notes and verification scope](releases/v0.2.0.md) remain the record for that artifact, not the restored-media 0.3.0 work.

**Upgrading from a testing alpha to 0.2.0:** that release introduced a dedicated signing key. Export Backup in the old app, keep the backup outside the app, uninstall the alpha, install 0.2.0, then Restore Backup. Android rejects an in-place update signed by a different key. Uninstalling without a backup deletes local training and photos.
