# Original media return: 22 October 2026

All 1,831 expected original video paths are recovered and imported locally. They cover 619 of the 1,345 catalog exercises. This completes the expected-path acquisition, not full catalog media coverage or application parity.

## Private return

The received file is `open-factor-original-media-2026-10-22.zip`, 2,775,893,458 bytes. Its SHA-256 matches the supplied sidecar and archive receipt:

```text
ecc3c44738d3e0350a80b004e45bd9204f52e052b13c07cc7506f4ae0e6fcb52
```

The verified export is extracted under ignored `inspection/private/mac-media-export/`. The original ZIP remains in Downloads. No returned scripts or native helpers were executed on Linux.

| Content | Received scope |
| --- | --- |
| Original videos | 1,831 MP4s for 619 exercises, 2,731,419,404 bytes |
| Original exercise photos | 1,831 matched WebPs and one unmatched WebP |
| Exercise catalog | All 1,345 records and 5,301 named entities |
| Original Markdown | All 1,500 files, including notes and locale variants |
| Older bundled database | Original compressed file, decoded database and 59 exported domain tables |
| Acquisition evidence | Manifest, checksums, provenance, validation and explicit missing coverage |

The acquisition records identify genuine Workouts 1.4.1, build 130, on an Apple Silicon Mac. Videos are unchanged files from the app's own cache. Photos and current Markdown are unchanged bundle files. No recordings, synthesized demos, account tokens or personal progress photos are imported.

## Linux verification and import

- Archive SHA-256 and every ZIP member's CRC passed.
- All 9,008 payload-file SHA-256 checks passed.
- All 3,663 media files match their manifest byte lengths and hashes.
- An independent Linux `ffmpeg -v error -xerror` full decode passes for all 1,831 videos and 1,832 images, with no failures.
- The returned source catalog, photos and all 1,500 Markdown files match the inspected Android APK byte-for-byte.
- Every expected video filename matches the original handoff map. All imported clip lists preserve its preferred-camera ordering, including Chin-up's c, a, b order.
- All 1,831 videos inside the final APK retain their acquired SHA-256 hashes.
- The full local APK builds. All 31 unit tests and lint pass.
- Both Android instrumentation tests pass through `AndroidJUnitRunner` on the fresh API 35 emulator. Gradle's connected-test installation failed with `INSTALL_FAILED_INSUFFICIENT_STORAGE` during its redundant full-APK install; direct installation and execution of the same test APK passed.
- The extended UI smoke pass succeeds with no active default network: all three Chin-up views play, backgrounding restores the Play button, and explicit replay works. Captured playback frames show body movement from each camera. The existing workout persistence and pause/resume smoke checks also pass.

The existing importer creates the app-ready pack:

```sh
python tooling/import_reference_apk.py /path/to/Workouts.apk \
	--video-directory inspection/private/mac-media-export \
	--output /path/to/new-reference-pack
```

The importer refuses to overwrite an existing pack. The prior local pack is retained privately as `inspection/private/reference-before-mac-media/`. The current pack is `openfactor/app/src/main/assets/reference/`, also ignored by Git.

All original videos use HEVC and are 1080 pixels wide. Most are 1920 pixels high; 24 have shorter frames. The local debug APK is 2,869,807,423 bytes. Original bytes are preserved rather than reduced through transcoding. Physical-device codec compatibility, installation space and a smaller distribution strategy remain separate work.

The native playback check exposed a lifecycle bug after changing cameras. The initial camera paused correctly on backgrounding, but a later selection retained a native player after returning to the app. `ExerciseMedia` registered its lifecycle observer only once, capturing the initial camera's playback state. The observer now follows both exercise identity and camera selection. The media smoke pass retains this failing scenario as a regression check.

The repeatable native UI smoke check now has an optional media pass:

```sh
python tooling/android_smoke.py --serial emulator-5582 \
	--output .amp/in/artifacts/media-smoke --media
```

The media pass requires the imported pack and a disposable emulator with setup completed or deferred. It disables Wi-Fi and mobile data, checks all three Chin-up camera choices, captures native playback, and checks that returning from the background requires an explicit Play action. It restores the prior network settings. Its recordings are test evidence from Open Factor, not source media or replacements for the originals.

## Unresolved coverage

The Mac export reports 1,331 exact exercise Info sheets opened. All exposed camera selectors were selected. Another 712 exercises without static paths exposed no demo controls during bounded visits, and 14 catalog entries were not located. These observations do not establish absent server media. The export's `coverage.json` retains `complete: false`.

There are 1,212 exercises with nonempty current Markdown. Captain's chair knee raise has an empty current file and its own text in the older bundled database. Both versions remain separate; the app does not silently substitute older prose. Another 132 exercises lack their own instructions in both bundled sources. One named note also lacks text.

The unmatched photo retains its original filename and a null exercise association. No missing ID characters or additional video suffixes are inferred.

## Delivery boundary

Recovered content and detailed acquisition evidence remain private. The original handoff inputs remain unchanged. The published alpha.4 APK predates this video import. No new release or media publication is part of this receipt, and the code license does not establish redistribution rights for the source content.
