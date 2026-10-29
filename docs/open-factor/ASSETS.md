# Asset provenance

## Current 0.3.0 media model

The current original-media experience stages recovered exercise photos, instructions, anatomy and equipment previews into a compact static pack. The inspected pack contains 1,832 exercise photos, 44 muscle SVGs, four body assets and 297 equipment images; its generated static payload is 32,986,897 bytes before APK compression. Equipment thumbnails are derived at up to 256 × 256 pixels; original imports remain unchanged. Original video remains separate: 1,831 MP4s for 619 exercises, requested over HTTPS automatically for the visible, resumed guide and cached with a 128 MiB bound, or optionally included by full mode. See [packaging modes, playback and cache](MEDIA-PACKAGING.md).

Default local `auto` builds select the static pack when it exists; `source` explicitly excludes recovered content. Source-only assets and original-media assets have distinct provenance and verification requirements. The media client and [service implementation](../../backend/media/README.md) do not establish deployment, upload completion or redistribution rights. Recovered originals remain outside Git and retain their original ownership.

## Historical public release 0.2.0

The 0.2.0 source-only public build included a separate, reviewed collection of 44 Everkinetic illustrations for 21 exercise variants, licensed CC BY-SA 3.0, and bundled DM Sans under OFL. See [licensed media and exact coverage](LICENSED-MEDIA.md). That artifact excluded `reference/**`; the source-only build mode and verifier retain this boundary. The source-only verifier rejects unreviewed assets and checks bundled preview hashes. This historical source-only payload does not describe the current static/full media modes.

Open Factor's application code inherits GNU GPLv3 from NuvioMedia/NuvioMobile. That license does not establish redistribution rights for content extracted from a separate application.

The public source uses Material icons and a custom geometric Open Factor launcher mark. The public descriptive dataset contains 1,345 exercise names and their muscle/equipment facts, plus 297 equipment entities, group memberships and literal preset/weight metadata. That descriptive projection includes no recovered source instructions, images, fonts or videos; the separately licensed public previews and DM Sans bundle are described above. `catalog-provenance.json` records the descriptive projection’s input hashes and scope.

The reproducible local importer at `tooling/import_reference_apk.py` reads an operator-supplied APK and creates `openfactor/app/src/main/assets/reference/`. Git ignores that directory. It records the source APK digest and each copied member's original path, byte length and SHA-256 in `provenance.json`. It preserves the full original exercise/equipment UUID index in `source-catalog.json` and provides a UI projection in `catalog.json`.

The inspected catalog has 1,345 exercise records. Exact filename-ID joins find images for 619 and Markdown files for 1,213. Only 1,212 of those instruction files contain text. The source APK also contains 297 equipment PNGs, 44 muscle-group SVGs and four body avatars. All are copied into the local pack with their original ownership retained. The importer also copies source fonts and Rive assets for local inspection; not every copied asset is rendered by the current application.

The static native trace resolves `videos/{exerciseId}_{thumbnailSuffix}.mp4`: 1,831 ordered paths for 619 exercises. A path is not a working URL. Anonymous Firebase requests failed App Check during the original investigation. The later private Mac return supplies all 1,831 original files from the genuine app's cache, with acquisition provenance and per-file validation. Camera labels remain unknown, so the UI numbers the views without inventing angle names.

The importer orders the preferred image first and the remaining suffixes alphabetically. It can ingest matching MP4 files from an authorized local export with `--video-directory /path/to/export`; either flat filenames or a `videos/` subdirectory works. It validates the MP4 signature and records each file digest. The current client plays bundled videos through the native asset data source; unbundled originals use the configured HTTPS service and bounded streaming cache. Missing clips retain an honest photo preview or unavailable state; there is no image-cycling approximation of video.

Reference-filled APKs are not produced by the automatic source-only CI workflow. After being told that alpha.4 includes extracted media, the user explicitly requested its publication. The manually published alpha.4 testing APK therefore includes the existing reference pack; the Git source tree still excludes it. This publication does not establish an asset license or relicense the original content under GPLv3. Replacement content or an explicit asset license remains necessary for a fully licensed open-source content pack.


The [22 October return receipt](media-handoff/RECEIPT.md) records independent archive, file-hash and decode checks, plus local import validation. The original input map remains unchanged. The returned media, instructions, older database text and acquisition evidence remain in ignored `inspection/private/mac-media-export/`; app-ready content remains in the ignored reference pack. No credentials or recovered content are added to Git.

All expected paths are recovered, but complete catalog media coverage is unverified. The export records 712 other exercises without demo controls during bounded visits and 14 unresolved catalog entries. Its older database contains text for the one empty current instruction file; both versions remain separate rather than silently substituting prose. Another 132 exercises lack their own instructions in both bundled sources.
