# Source Region projection, 22 October 2026

The catalog projection now retains `regionTrained` as `properties.Region`. Existing catalog records can prefill the rich duplicate wizard's compound/isolation region instead of requesting information that was already recoverable. This is source metadata, not an inferred training algorithm.

## Evidence and reproduction

The retained APK is `S/phone/com.sbs.train/base.apk`, where `S` is defined in [the baseline audit](whole-app-parity-2026-10-22.md). Member `assets/flutter_assets/packages/macrofactor/assets/import/app_file.json` supplies each exercise's `regionTrained` ID; `uuidIndex` resolves the names. APK SHA-256 is `32df7bbb6939444957d150641e48c2a2788f8fd6c85bc111df8d6047cd044396`; catalog SHA-256 is `65f1d2450e6a3c01542210705b929cf4c9544af1cadfd6374474fd1633c1e00f`, matching the existing tracked provenance.

The [existing importer](../../../tooling/import_reference_apk.py), introduced with the descriptive catalog, omitted this field. Its new `--facts-only` option regenerates public facts without extracting instruction prose, artwork, fonts, videos or the full raw catalog:

```sh
python3 tooling/import_reference_apk.py "$SOURCE_APK" \
  --output "$NEW_METADATA_DIRECTORY" \
  --public-data-output "$NEW_FACTS_DIRECTORY" --facts-only
```

Only the generated `catalog.json` changed in tracked assets. All 1,345 ordered IDs and existing properties, equipment, names, muscles, metrics, aliases, bodyweight coefficients and rankings remain identical. Generated equipment and provenance are semantically identical and were not copied over existing files. Source Regions are Upper body 726, Lower body 441, Core 132, Full body 37 and None 9. None remains unresolved; it does not become an invented type.

## Runtime compatibility and checks

[WorkoutViewModel catalog initialization](../../../openfactor/app/src/main/java/org/openfactor/app/Data.kt) fills only missing Region values on older private-pack records, matched by exact ID against public facts. Existing imported Region and every other imported field remain unchanged. It does not rewrite the private pack, media, stored exercises, body/photo records or backups. New reference packs get Region through the importer directly.

The [native catalog test](../../../openfactor/app/src/androidTest/java/org/openfactor/app/TrainingFlowTest.kt) checks contrasting Upper/Lower/Full type prefill and unchanged imported fields. The existing JVM duplicate test still requires a choice for an actually missing Region. [Custom UI acceptance](../../../tooling/android_custom_exercise_smoke.py) now checks the catalog's recovered Isolation Lower prefill before the long-alias cancel/recovery journey.

Projection validation passed against every raw source record and the pre-change catalog; result is `.amp/in/artifacts/parity-implementation-07/projection-result.json`. Public/private assembly, lint and 51 JVM tests passed; six public native tests and the focused private-pack catalog test passed. Private catalog/raw-catalog hashes are unchanged. The uninterrupted offline custom/duplicate flow passed (`final/result.json`); recovered prefill and the added Region detail were inspected in `final/18-catalog-type-prefilled.png` and `region-detail/21-catalog-region-detail.png`. The detail capture shows normal mid-scroll clipping of preceding aliases at the list boundary; the Region label/value is unobscured.

This closes the recoverable Region omission described in [the duplicate unit](exercise-duplicate-01.md), not unobserved source duplicate/Create outcomes. **Next correction:** the inherited 300-character alias cap still blocks creating a copy with all 515 source alias characters; cancel/shortening acceptance does not establish lossless Create. The parent requested removing that unsupported restriction, without a per-copy exception or another guessed limit. Detail also still displays raw Type and a separate Region rather than the observed combined type label. Modified/Archived behavior, equipment/left-right rest policies, smart programming and the wider audit backlog remain unaccepted. Changes are local and unpushed; no recovered media was changed or published.
