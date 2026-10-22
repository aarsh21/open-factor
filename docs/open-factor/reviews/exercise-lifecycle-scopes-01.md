# Exercise lifecycle and classification, 22 October 2026

This unit implements the recovered Modified predicate, override reset, reversible custom archive, and native Info classification. It preserves the [separate exercise pin and history notes](exercise-pinned-notes-01.md). Equipment and left/right override application remain unresolved.

## Retained binary contracts

The source is the retained `private/lib/arm64-v8a/libapp.so`, SHA-256 `870dbb392efa8658b565303f8a83ffff470115cf79a52535f83ba138876a12b6`. The inspection root is the private `S` directory defined in the baseline audit. No source application, account, or phone was operated. These findings come from static ARM64 inspection and retained metadata, not newly executed source outcomes or new REA Evidence records.

- Modified at `0xe7df94–0xe7e054` checks a nonempty `weightConfigOverrideById`, or nonnull rest, split rest, weight-match, rep-range-extension, warm-up-automation, or warm-up-scheme overrides. It excludes note, Do Not Recommend, units, split state, bars, additional weights, and selected equipment groups. The local model currently supports only the scalar rest override, including explicit zero.
- Reset at `0xfca740–0xfca87c` clears those same overrides. It retains note, Do Not Recommend, split state, units, bars, additional weights, and equipment-group selections. Local reset clears only the supported rest override.
- The catalog wrapper at `0xdf3360` passes false to the wrapper constructor `0x6d2648`. The custom importer at `0x6d2624` passes true. Constructor stores at `0x6d26c4–0x6d26c8` bind this custom flag to `+0xb`. The settings archive row at `0xfa46c4–0xfa46cc` gates on that flag. Custom-only archive is therefore supported by the call path, not inferred from the persistence location.
- Archive at `0x12ce5e0` sets raw exercise `+0xa3` true. Unarchive at `0x12cea48` sets it false. The path `0xfa5b34 → 0x6d2564` persists custom exercise records under `users/{id}/customExercises`. The JSON builder at `0x6ca480` binds `+0xa3` to `archived`. Source settings bind the history-preservation explanation to this action.
- Info at `0xf9f564` obtains effective region at `0xa55774`, category at `0xa55674`, and native label at `0xdf6084`. Its five English labels are Compound Upper, Compound Lower, Upper Isolation, Lower Isolation, and Core. Full-body compound maps to Compound Lower. The separately observed six custom wizard choices remain unchanged.

For raw Core region, effective region checks movement patterns, not featured muscles. Ab Accessory or the source spelling `Oblique Acessory` preserves Core. Otherwise, the native mapper converts accessory patterns to muscle categories, retains null results, and selects a sole distinct upper or lower category. Empty, mixed, and null-containing results preserve Core. The lower-back muscle switch uses Int32 table ref199466 at file offset `0x3c7ba6`. The entire 74-record cluster `0x3c7497..0x3c9cb6` was parsed before using its targets. Muscle index10 targets `0xa5597c`, which loads lower at `0xa55980`. This is not an assumed sequential case order.

Excluded evidence is under `.amp/in/artifacts/exercise-lifecycle-source/`. Raw and annotated files cover Modified, reset, wrappers, archive actions and persistence, scopes, effective region, category, labels, and the validated muscle jump table. The older worker's `config-functions.json` and `config-json-body{,-annotated}.txt` establish serialized names only. They do not establish defaults or save behavior.

The disassembly recipe uses installed `llvm-objdump -D --no-show-raw-insn --arch-name=aarch64 --start-address=... --stop-address=... "$S/private/lib/arm64-v8a/libapp.so"`. Annotations reuse `S/browse-group-recovery/annotate.py` with `PYTHONDONTWRITEBYTECODE=1`.

## Local compatibility and factual projection

Archived custom exercises remain in `WorkoutViewModel.exercises()` for existing draft, active, program, and history resolution. Library scopes and contextual library pickers hide them. The parent separately fixes the new-workout `RoutineBuilder` candidate filter in `dbaf70ea`; saved-reference resolution stays unfiltered. Archived settings show Unarchive instead of active settings rows. Archive does not navigate away or delete notes, media identity, or saved objects. Duplicates create fresh unarchived records. Catalog archive calls are no-ops.

Modified excludes pins and Do Not Recommend. Reset retains those values and saved training records. Legacy backups default absent `archived` to false. No migration rewrites old copied notes, which cannot be distinguished from user-authored workout notes.

The public catalog adds only descriptive `Movement Patterns` facts. `Exercise.movementPatterns` returns null for an absent field, an empty list for explicit `None`, and the unmodified source names otherwise. Pull names are exactly `Horizontal Pull` and `Vertical Pull`. Catalog loading fills only missing Region and Movement Patterns from the bundled facts. It never replaces present facts, edits custom facts, or modifies a private pack.

All eight Jefferson-curl records retain raw Region Core and raw Lower Back Accessory. The effective predicate now resolves their lower-body Info and duplicate categories. Nine records with absent source Region remain unresolved. No exercise-ID exception or title/muscle inference is used.

Facts regenerate from an authorized caller-supplied APK with:

```sh
python3 tooling/import_reference_apk.py "$APK" --facts-only \
	--output "$NEW_EMPTY_OUTPUT" --public-data-output "$NEW_FACTS_DIRECTORY"
```

The input member is `assets/flutter_assets/packages/macrofactor/assets/import/app_file.json`. Existing `catalog-provenance.json` retains APK and raw member hashes. The regenerated catalog is identical to the previous catalog after removing only Movement Patterns. Equipment and provenance JSON are semantically unchanged. The projected catalog SHA-256 is `d0571bba1526067b8a2092000439ebe21b27adad5b7c54007c9b22e39d9bc093`. Facts-only output contains no instructions, media, fonts, animations, or raw catalog.

## Verification and restoration

Evidence is under `.amp/in/artifacts/exercise-lifecycle-validation/`. The public build passes assembly, lint, all 53 JVM tests, and Android-test assembly. Its APK contains exactly three assets: catalog, equipment, and catalog-provenance JSON. Private assembly and Android-test assembly pass with the ignored, read-only reference-pack symlink. The symlink is removed afterward. Commands run from `openfactor/`, guarded by the parent's shared `1.lock`, with `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.compiler.execution.strategy=in-process`. No recovered original is in the source-only APK.

Both installed variants pass all seven native tests on leased emulator-5584. The archive/reset regression covers catalog no-op, durable custom archive and unarchive, saved identity, backup, existing active resolution, pinned note and Do Not Recommend retention, and unchanged history, draft, programs, and body/photo records. The existing body/photo tests also run. JVM controls include legacy absent archive, unarchived fresh duplicate, explicit zero override, mixed known/null patterns, conflicting title/muscle facts, explicit-empty versus absent patterns, full-body Info versus wizard, and unresolved facts.

Complete offline UI flows pass for the public APK at normal and 1.3× font, and for the private-font APK at 1.3×. They exercise Modified excluding pin-only/DNR-only settings, archive-only mutation, Archived scope, process restart, existing archived active-entry resolution, picker exclusion, unarchive, reset, and empty Modified. Four real catalog controls verify compound and isolation Jefferson curls, full-body Burpee, and unresolved Band lateral raise. Saved state is unchanged by classification browsing. Captures and XML are inspected for the affected settings, scopes, logger/picker context, reset, and classification. Enlarged public and private footer captures show the full pin/history explanation after vertical scroll. Horizontal filter/muscle strips and vertical list boundaries intentionally crop offscreen content. No blocking layout defect was found in these owned surfaces. The private variant has no separate normal-font UI run in this unit.

The parent combined source-only APK at local main `96f69fe6` is also installed and checked. SHA-256 is `18202bbcf6f7007030b6ab6f01defe175c36183e433d7e02f828302adeced569`. The real `RoutineBuilder` screen excludes an archived candidate, accepts the active control, and saves only that active ID. The previous saved routine still references the archived exercise, and every other saved field is unchanged. The candidate-screen render is inspected. The saved-list capture includes the preserved archived reference; the new card is partly below the viewport, so that capture does not certify its full layout. This is not a new combined JVM/lint claim; the parent owns those results.

After every native/UI run, exact incoming app files are restored. Final `release.json` verifies the original 28,869-byte XML, SHA-256 `ba977aa4bda91235b549ae8a81635048059f02c915a5d0b085ebcfbc0f8ec79e`, and the unchanged zero-byte `files/profileInstalled`. Font 1.0, automatic rotation 1, user rotation 0, Wi-Fi 0, and mobile data 0 match the incoming settings. The final crash buffer is empty. The owned external UI dump is removed. The AVD is explicitly released to Dashboard with the parent combined public APK installed. No source device or extra AVD is used.

Failed attempts remain separate. The first public build disappears during `dexBuilderDebug` without a terminal result or APK; it is not counted as a pass. A four-second up-to-date run stays public after a mistaken local symlink path; only the corrected 32-second private packaging run counts. The first public UI attempt taps the search field instead of a same-named result. The first private attempt assumes raw Region is in the initial viewport, although a legacy pack appends its missing-field fallback after the other properties. Corrected complete flows pass without app changes. Each failed attempt also restores exact files, font, and network. Temporary verification scripts are removed after validation.

## Remaining source gaps

1. Recover the effective defaults, application, validation, and save path for selected equipment groups, bars, additional weights, weight matching, split state, and left/right rest. Editor rows and serialized names alone are insufficient. This unit adds none of those guessed policies.
2. Recover the richer per-rest-kind timer representation. The local scalar rest override, existing 0–1800 validation, and program-rest precedence predate this unit and are not newly source-certified.
3. Recover authenticated catalog facts for the nine absent-region records. Do not infer their categories from their names or muscles.
4. Observe source archive, unarchive, reset, and settings Save outcomes before claiming runtime equivalence. Static predicates and mutations establish this implementation's bounded contract, not every source interaction or ranking rule.

Lifecycle is local commit `323abffb`; classification and factual projection are separate local commit `00cab8f9`. The settings dependencies are original `fe8abe76` and `12484020`, locally cherry-picked as `cc1fff0f` and `4a5e959a`. The parent already has its own copies and must not integrate the duplicates. The parent integrates the unit into its local combined APK; this unit does not push, publish, or deploy anything.
