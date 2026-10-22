# Exercise heading overflow, 22 October 2026

The saved **Weighted Roman Chair Hip Hinge (Copy)** title clipped its Copy suffix in Info, especially at 1.3× text. The before capture is `.amp/in/artifacts/parity-implementation-09/custom-detail/23-combined-type-large-font.png`. Its inspection confirms glyph fragments below the second title line. This is a demonstrated local presentation defect, not recovered source overflow behavior.

[PageHeading in Ui.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Ui.kt) now allows three lines and uses an explicit ellipsis beyond them. Fonts, sizes, spacing, actions, the Nuvio navigation and the parent app-night configuration remain unchanged. The extra line keeps the saved Copy suffix readable without shrinking text; extreme names remain bounded. Full title text remains in accessibility semantics. The three-line limit is a local presentation choice, not a claim of exact source behavior.

## Verification and retained evidence

Evidence is under `.amp/in/artifacts/parity-implementation-10/`:

- Clean public and private-pack assembly, lint and all 51 JVM tests passed (`build-public.log`, `build-full-pack.log`). Six public native tests passed (`android-public-tests.log`). The earlier incremental package transition from the large private APK failed with an AGP integer overflow; `build-public-incremental-failure.log` is retained. A clean public rebuild passed without an app workaround.
- The uninterrupted offline UI check passed on owned emulator-5584 (`ui.log`, `ui/result.json`). It covered saved-copy Settings, Info and Details at default and 1.3× text, Close Info and Back, plus Dashboard and grouped Settings controls. A synthetic extreme title ends cleanly with an ellipsis in Settings and Info while retaining its full accessibility text. All ten final captures were inspected. The saved Copy title is fully drawn in both text sizes; Back, Close, star and tabs remain unobscured. Content at vertical scroll boundaries and behind the chosen floating navigation is not heading truncation.
- The check compared saved training/body/photo/default records before and after navigation, then restored exact original synthetic preference bytes, state and font scale after removing the temporary extreme-title record. It did not write photo files. `identity.log` records emulator and installed public APK identity separately from the built private APK.

An initial UI probe assumed Python title-casing would change an all-caps `FINAL` suffix. The app preserves that suffix. The failed partial probe and its exact restoration are retained in `ui-title-assumption/` and `ui-title-assumption.log`; they are not the final pass. The corrected probe used the expected unchanged suffix.

This unit changes shared heading overflow only. It does not close Modified/Archived behavior, equipment/left-right rest policy, source duplicate/Create outcomes, the 17 unresolved catalog classifications, or the wider functional and UX parity backlog. No parent media/smoke/ledger or body files were changed. Work is local and unpushed.
