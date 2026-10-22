# Asset provenance

Open Factor's application code inherits GNU GPLv3 from NuvioMedia/NuvioMobile. That license does not establish redistribution rights for content extracted from a separate application.

The public source uses Material icons and a custom geometric Open Factor launcher mark. The public descriptive dataset contains 1,345 exercise names and their muscle/equipment facts, plus 297 equipment entities, group memberships and literal preset/weight metadata. It includes no source instructions, images, fonts or videos. `catalog-provenance.json` records input hashes and the projection scope.

The reproducible local importer at `tooling/import_reference_apk.py` reads an operator-supplied APK and creates `openfactor/app/src/main/assets/reference/`. Git ignores that directory. It records the source APK digest and each copied member's original path, byte length and SHA-256 in `provenance.json`. It preserves the full original exercise/equipment UUID index in `source-catalog.json` and provides a UI projection in `catalog.json`.

The inspected catalog has 1,345 exercise records. Exact filename-ID joins find images for 619 and Markdown files for 1,213. Only 1,212 of those instruction files contain text. The source APK also contains 297 equipment PNGs, 44 muscle-group SVGs and four body avatars. All are copied into the local pack with their original ownership retained. The importer also copies source fonts and Rive assets for local inspection; not every copied asset is rendered by the current application.

The static native trace resolves `videos/{exerciseId}_{thumbnailSuffix}.mp4`: 1,831 ordered paths for 619 exercises. A path is not a working URL. Anonymous Firebase requests failed App Check during the original investigation. The later private Mac return supplies all 1,831 original files from the genuine app's cache, with acquisition provenance and per-file validation. Camera labels remain unknown, so the UI numbers the views without inventing angle names.

The importer orders the preferred image first and the remaining suffixes alphabetically. It can ingest matching MP4 files from an authorized local export with `--video-directory /path/to/export`; either flat filenames or a `videos/` subdirectory works. It validates the MP4 signature and records each file digest. Asset-backed video files are copied into the app's cache for native playback. Missing clips retain an honest photo preview; there is no image-cycling approximation of video.

Reference-filled APKs are not produced by the automatic source-only CI workflow. After being told that alpha.4 includes extracted media, the user explicitly requested its publication. The manually published alpha.4 testing APK therefore includes the existing reference pack; the Git source tree still excludes it. This publication does not establish an asset license or relicense the original content under GPLv3. Replacement content or an explicit asset license remains necessary for a fully licensed open-source content pack.


The [22 October return receipt](media-handoff/RECEIPT.md) records independent archive, file-hash and decode checks, plus local import validation. The original input map remains unchanged. The returned media, instructions, older database text and acquisition evidence remain in ignored `inspection/private/mac-media-export/`; app-ready content remains in the ignored reference pack. No credentials or recovered content are added to Git.

All expected paths are recovered, but complete catalog media coverage is unverified. The export records 712 other exercises without demo controls during bounded visits and 14 unresolved catalog entries. Its older database contains text for the one empty current instruction file; both versions remain separate rather than silently substituting prose. Another 132 exercises lack their own instructions in both bundled sources.
