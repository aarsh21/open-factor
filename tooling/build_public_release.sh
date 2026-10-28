#!/usr/bin/env bash
# Signing material is intentionally external to the repository.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
: "${OPENFACTOR_KEYSTORE:?Set OPENFACTOR_KEYSTORE to the persistent release keystore}"
: "${OPENFACTOR_PASSWORD_FILE:?Set OPENFACTOR_PASSWORD_FILE to its private password file}"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK}"
build_tools="${OPENFACTOR_BUILD_TOOLS:-$ANDROID_HOME/build-tools/35.0.0}"
output_dir="${OPENFACTOR_RELEASE_DIR:-$project_dir/openfactor/app/build/public-release}"
mkdir -p "$output_dir"
cd "$project_dir/openfactor"
./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease -PopenfactorMediaMode=source
"$build_tools/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$output_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$OPENFACTOR_KEYSTORE" --ks-key-alias open-factor \
    --ks-pass "file:$OPENFACTOR_PASSWORD_FILE" \
    --out "$output_dir/open-factor.apk" "$output_dir/aligned.apk"
rm "$output_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$output_dir/open-factor.apk" > "$output_dir/signature.txt"
python3 "$project_dir/tooling/verify_public_apk.py" "$output_dir/open-factor.apk" --output "$output_dir/artifact.json"
(cd "$output_dir" && sha256sum open-factor.apk > SHA256SUMS)
echo "Signed release: $output_dir/open-factor.apk"
