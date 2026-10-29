# Publishing downloadable Android releases

The stable download is always:

**https://github.com/aarsh21/open-factor/releases/latest/download/open-factor.apk**

A CI artifact is not a published app release. `.github/workflows/open-factor-release.yml` attaches the signed original-media APK to the repository's **Releases** section, along with `SHA256SUMS`, the media-verification receipt and the public signing-certificate receipt.

## Normal release

1. Increase **both** `versionName` and `versionCode` in `openfactor/app/build.gradle.kts`. Stable names use `major.minor.patch`; never reuse an Android version code for a new release.
2. Add reviewed changes, update/backup guidance and known limitations in `docs/open-factor/releases/v<version>.md`. Update the README's versioned release link. Do not invent artifact hashes or verification results before the build runs; attached CI-generated receipts identify the actual APK.
3. Merge the reviewed changes into `main`.
4. The complete **Open Factor Android** push workflow must pass for that exact main commit: build/unit/lint/public-APK checks and the API26/API35 device matrix.
5. Its successful completion starts **Open Factor Release**. The release workflow builds and signs the original-media variant, runs release unit tests and lint, verifies the packaged assets and certificate, then creates `v<version>` targeting that main commit with a direct APK download in its notes.

Already published versions with an uploaded APK are left unchanged. Incomplete/draft releases, missing release notes, stale/non-main commits, failed CI, missing credentials, invalid media and non-increasing versions fail or skip rather than overwrite an existing download. Unversioned/docs-only merges do not create duplicate releases. If main advances during a build, the successor commit owns publication. Concurrent publication jobs are serialized.

The workflow never publishes from a PR or fork workflow run. Manual dispatch is restricted to `main` and requires successful full push CI for the same commit. No untrusted PR artifacts are consumed by the signing job. The release workflow itself must be merged into the default branch before GitHub can trigger it.

## Signing credentials

These repository Actions secrets are configured with the existing, persistent release identity:

- `OPENFACTOR_RELEASE_KEYSTORE_BASE64`: base64-encoded keystore with alias `open-factor`.
- `OPENFACTOR_RELEASE_KEYSTORE_PASSWORD`: its password.

The current certificate SHA-256 is `26d674e28f0df6bfe2fe4a9fc4e2fed332621521d2459331eea645c944e22505`. Both the baseline and finished APK must match it. Changing keys breaks normal in-place updates and requires an explicit migration decision.

Secrets are exposed only to the trusted-main signing step. That step writes private temporary files with restrictive permissions, removes secret values from its environment after materializing them, and deletes the files with an EXIT trap. Keystores/passwords are never committed, included in release assets or included in uploaded build evidence. Keep an independent secure backup of the existing signing material; repository secrets are not a recovery mechanism.

## Original-media bootstrap

The ignored source reference pack is not available on hosted runners. Instead, the release workflow uses the reviewed **0.8.0** APK, pinned to SHA-256 `2c804aebbe3877edc7a728348422e05890fa0a47389fa14760e8ce4f23fb941c`, as a static-media input. After signature/checksum validation, the full media verifier checks its manifest and backend video identities before only manifest-listed static assets are staged. The original provenance manifest and asset bytes are preserved, and no APK code is reused. Videos remain streamed/cached, not bundled.

This pin is deliberately not the moving `latest` release. Media changes require a reviewed baseline and verifier update. See [media packaging](MEDIA-PACKAGING.md).

Local builds can exercise the same path:

```sh
export OPENFACTOR_MEDIA_PACK_APK=/absolute/path/to/downloaded/open-factor.apk
export OPENFACTOR_MEDIA_PACK_SHA256=2c804aebbe3877edc7a728348422e05890fa0a47389fa14760e8ce4f23fb941c
# Also supply ANDROID_HOME, OPENFACTOR_KEYSTORE and OPENFACTOR_PASSWORD_FILE privately.
bash tooling/build_media_release.sh
```

The optional input variables must be supplied together. Without them, the existing local original-static staging path is unchanged. Source builds do not accept this static-only reuse option.

## Verify without publishing / recover a failed run

The manual workflow defaults to **verify only**: it builds, signs, validates and uploads an Actions artifact without creating or replacing a GitHub Release, even for a version already published.

```sh
gh workflow run open-factor-release.yml --ref main -f verify_only=true
```

To retry an unpublished version after fixing a transient failure, dispatch with `verify_only=false` or rerun the failed release job. A real publication still requires a new version and passing CI for the exact current main commit. If a tag/release was partially created, inspect it and repair it deliberately; this workflow does not delete tags, clobber release assets or silently change an existing APK. Do not create a replacement signing identity to work around missing secrets.

## Local verification of this automation

- All **17 Python tooling tests** passed with the explicitly supplied public baseline APK. This includes path/duplicate/digest/boundary/output-ownership rejection and real full-manifest reuse. Without the optional public-baseline environment variable, that one integration case skips.
- The local signed-release build succeeded using only the pinned downloaded APK for static media. All **3,394** media files and the complete provenance manifest were byte-identical to the baseline; the output retained the existing signing certificate. The produced APK matched the published 0.8.0 hash in this local build.
- Release unit tests/lint and shell/YAML syntax checks were exercised. Hosted-runner signing/publication is not established by these local checks; inspect the **Open Factor Release** workflow's result after merge or a verification-only dispatch.
