# Bundled licensed exercise illustrations

The public app includes 44 original PNG illustrations by **Everkinetic**, distributed by [wger](https://wger.de), for 21 explicitly reviewed exercise variants. The images use [Creative Commons Attribution-ShareAlike 3.0 Unported](https://creativecommons.org/licenses/by-sa/3.0/); they are unmodified. This license applies to these images independently of the application code's GPLv3 license. No endorsement by the artist or wger is implied.

The app's Settings → Exercise media credits includes the attribution, source links, and complete license text available offline. The machine-readable [`exercise-previews.json`](../../openfactor/app/src/main/assets/exercise-previews.json) records every original URL, author, license, image ID, byte size and SHA-256 hash. The public APK excludes the private reference pack by default.

| Exercise | Images | Original source |
| --- | ---: | --- |
| Barbell bench press | 2 | [wger 73](https://wger.de/en/exercise/73/view/) |
| Dumbbell bench press | 2 | [wger 75](https://wger.de/en/exercise/75/view/) |
| Close grip bench press | 2 | [wger 76](https://wger.de/en/exercise/76/view/) |
| Barbell biceps curl | 2 | [wger 91](https://wger.de/en/exercise/91/view/) |
| Standing dumbbell biceps curl | 2 | [wger 92](https://wger.de/en/exercise/92/view/) |
| Cable straight bar biceps curl | 2 | [wger 95](https://wger.de/en/exercise/95/view/) |
| Chin-up | 2 | [wger 152](https://wger.de/en/exercise/152/view/) |
| Dumbbell walking lunge | 4 | [wger 206](https://wger.de/en/exercise/206/view/) |
| EZ bar skull crusher | 2 | [wger 246](https://wger.de/en/exercise/246/view/) |
| Cross-arm grip front squat | 2 | [wger 257](https://wger.de/en/exercise/257/view/) |
| Standing dumbbell hammer curl | 2 | [wger 272](https://wger.de/en/exercise/272/view/) |
| Cable rope hammer curl | 2 | [wger 275](https://wger.de/en/exercise/275/view/) |
| Standing dumbbell lateral raise | 2 | [wger 348](https://wger.de/en/exercise/348/view/) |
| Lying hamstring curl | 2 | [wger 365](https://wger.de/en/exercise/365/view/) |
| Seated hamstring curl | 2 | [wger 366](https://wger.de/en/exercise/366/view/) |
| EZ bar preacher curl | 2 | [wger 465](https://wger.de/en/exercise/465/view/) |
| Seated barbell overhead press | 2 | [wger 566](https://wger.de/en/exercise/566/view/) |
| Seated dumbbell overhead press | 2 | [wger 567](https://wger.de/en/exercise/567/view/) |
| Barbell shrug | 2 | [wger 571](https://wger.de/en/exercise/571/view/) |
| Standing dumbbell shrug | 2 | [wger 572](https://wger.de/en/exercise/572/view/) |
| Barbell good morning | 2 | [wger 1392](https://wger.de/en/exercise/1392/view/) |

Only these reviewed joins receive an illustration. Similar exercise names, equipment and variants are never guessed. The cross-arm front squat image is attached specifically to Cross-arm grip front squat. Exercises without a reviewed preview use the app's normal icon fallback. The baseline descriptive catalog stays unchanged; the application overlays only the exact preview mapping at runtime.

Verify all bundled bytes without network access:

```sh
python tooling/import_licensed_previews.py --verify
```

Refresh the pinned image IDs and record updated provenance from the public API:

```sh
python tooling/import_licensed_previews.py --refresh
```

The importer rejects an image whose exercise ID, creator, license or AI-generated flag differs from the vetted values. Review any refreshed image bytes and mapping before release. It preserves the original PNG bytes, so the manifest hashes can be checked against the source downloads. This bounded pack is illustration coverage, not full photo or demonstration-video coverage.
