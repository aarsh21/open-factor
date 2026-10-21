# Asset provenance

Open Factor's application code inherits GNU GPLv3 from NuvioMedia/NuvioMobile. That license does not establish redistribution rights for content extracted from a separate application.

The public source uses Material icons and a custom geometric Open Factor launcher mark. A small baseline list contains exercise names and categories; it includes no extracted prose, images, fonts or videos.

The reproducible local importer at `tooling/import_reference_apk.py` reads an operator-supplied APK and creates `openfactor/app/src/main/assets/reference/`. Git ignores that directory. It records the source APK digest and each copied member's original path, byte length and SHA-256 in `provenance.json`. It preserves the full original exercise/equipment UUID index in `source-catalog.json` and provides a UI projection in `catalog.json`.

The inspected catalog has 1,345 exercise records. Exact filename-ID joins find images for 619 and Markdown for 1,213. The source APK also contains 297 equipment PNGs, 44 muscle-group SVGs and four body avatars. All are copied into the local pack with their original ownership retained. The importer also copies source fonts and Rive assets for local inspection; not every copied asset is rendered by the current application.

The static native trace resolves `videos/{exerciseId}_{thumbnailSuffix}.mp4`: 1,831 ordered paths for 619 exercises. A path is not a working URL. A standard anonymous Firebase metadata request returned HTTP 401 (invalid App Check token); no source videos or playback have been verified. Camera labels remain unknown, so the UI numbers the views without inventing angle names.

The importer orders the preferred image first and the remaining suffixes alphabetically. It can ingest matching MP4 files from an authorized local export with `--video-directory /path/to/export`; either flat filenames or a `videos/` subdirectory works. It validates the MP4 signature and records each file digest. Asset-backed video files are copied into the app's cache for native playback. Missing clips retain an honest photo preview; there is no image-cycling approximation of video.

Public distribution of a reference-filled APK is deliberately not part of the source-only CI workflow. A future redistributable content pack needs an explicit asset license or replacement content with suitable permissions.
