#!/usr/bin/env bash
# Build the original static exercise pack; videos remain on the HTTPS media service.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
: "${OPENFACTOR_KEYSTORE:?Set OPENFACTOR_KEYSTORE to the persistent release keystore}"
: "${OPENFACTOR_PASSWORD_FILE:?Set OPENFACTOR_PASSWORD_FILE to its private password file}"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK}"
build_tools="${OPENFACTOR_BUILD_TOOLS:-$ANDROID_HOME/build-tools/35.0.0}"
output_dir="${OPENFACTOR_RELEASE_DIR:-$project_dir/openfactor/app/build/media-release}"
mkdir -p "$output_dir"
cd "$project_dir/openfactor"
media_args=(-PopenfactorMediaMode=static)
if [[ -n "${OPENFACTOR_MEDIA_PACK_APK:-}" || -n "${OPENFACTOR_MEDIA_PACK_SHA256:-}" ]]; then
    : "${OPENFACTOR_MEDIA_PACK_APK:?Set the published baseline APK path}"
    : "${OPENFACTOR_MEDIA_PACK_SHA256:?Set its reviewed SHA-256}"
    media_args+=("-PopenfactorMediaPackApk=$OPENFACTOR_MEDIA_PACK_APK" "-PopenfactorMediaPackSha256=$OPENFACTOR_MEDIA_PACK_SHA256")
fi
./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease "${media_args[@]}"
"$build_tools/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$output_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$OPENFACTOR_KEYSTORE" --ks-key-alias open-factor \
    --ks-pass "file:$OPENFACTOR_PASSWORD_FILE" \
    --out "$output_dir/open-factor.apk" "$output_dir/aligned.apk"
python3 -c 'from pathlib import Path; import sys; Path(sys.argv[1]).unlink()' "$output_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$output_dir/open-factor.apk" > "$output_dir/signature.txt"
python3 "$project_dir/tooling/verify_media_apk.py" "$output_dir/open-factor.apk" --output "$output_dir/artifact.json"
(cd "$output_dir" && sha256sum open-factor.apk > SHA256SUMS)
echo "Signed media release: $output_dir/open-factor.apk"
