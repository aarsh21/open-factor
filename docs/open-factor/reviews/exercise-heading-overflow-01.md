# Exercise heading overflow, 22 October 2026

The saved **Weighted Roman Chair Hip Hinge (Copy)** title clipped its Copy suffix in Info, especially at 1.3× text. The before capture is `.amp/in/artifacts/parity-implementation-09/custom-detail/23-combined-type-large-font.png`. Its inspection confirms glyph fragments below the second title line. This is a demonstrated local presentation defect, not recovered source overflow behavior.

[PageHeading in Ui.kt](../../../openfactor/app/src/main/java/org/openfactor/app/Ui.kt) now allows three lines and uses an explicit ellipsis beyond them. Fonts, sizes, spacing, actions, the Nuvio navigation and the parent app-night configuration remain unchanged by that bounded unit. Public-font checks show the saved Copy suffix without shrinking text; extreme names remain bounded. Full title text remains in accessibility semantics. The three-line limit is a local presentation choice, not a claim of exact source behavior. Private-font acceptance requires the separate heading-role correction below.

## Verification and retained evidence

Evidence is under `.amp/in/artifacts/parity-implementation-10/`:

- Clean public and private-pack assembly, lint and all 51 JVM tests passed (`build-public.log`, `build-full-pack.log`). Six public native tests passed (`android-public-tests.log`). The earlier incremental package transition from the large private APK failed with an AGP integer overflow; `build-public-incremental-failure.log` is retained. A clean public rebuild passed without an app workaround.
- The uninterrupted offline public-build UI check passed on owned emulator-5584 (`ui.log`, `ui/result.json`). It covered saved-copy Settings, Info and Details at default and 1.3× text, Close Info and Back, plus Dashboard and grouped Settings controls. A synthetic extreme title ends cleanly with an ellipsis in Settings and Info while retaining its full accessibility text. All ten final captures were inspected. The saved Copy title is fully drawn in both text sizes; Back, Close, star and tabs remain unobscured. Content at vertical scroll boundaries and behind the chosen floating navigation is not heading truncation.
- The check compared saved training/body/photo/default records before and after navigation, then restored exact original synthetic preference bytes, state and font scale after removing the temporary extreme-title record. It did not write photo files. `identity.log` records emulator and installed public APK identity separately from the built private APK.

An initial UI probe assumed Python title-casing would change an all-caps `FINAL` suffix. The app preserves that suffix. The failed partial probe and its exact restoration are retained in `ui-title-assumption/` and `ui-title-assumption.log`; they are not the final pass. The corrected probe used the expected unchanged suffix.

This unit changes shared heading overflow only. It does not close Modified/Archived behavior, equipment/left-right rest policy, source duplicate/Create outcomes, the 17 unresolved catalog classifications, or the wider functional and UX parity backlog. No parent media/smoke/ledger or body files were changed. Work is local and unpushed.

## Parent integration: private-font detail heading

The parent's public and private semantic checks passed, but inspection of the private-font render still showed an ellipsized Copy suffix. Closing Info with X removes its Back control; `PageHeading` had treated that absence as a root-page role and selected the wide uppercase display face. The public fallback font hid this difference. The complete title remained in accessibility text, so a text-presence assertion could not detect the rendering defect.

Retained `S/feature-tour/screens/exercise-info.png` shows a compact mixed-case source title; `S` is defined in [the baseline audit](whole-app-parity-2026-10-22.md). Info now explicitly requests detail typography independently of its Close control. Existing root headings keep their display font, and the three-line ellipsis still bounds extreme names. Before renders and semantic results remain under `.amp/in/artifacts/type-heading-integrated/before-private/`; those results are not private-font visual acceptance.

The corrected combined build passes public/private assembly, lint and 51 JVM tests, plus six Android tests and a focused offline UI run on each variant. The runs cover normal/1.3× Settings/Info/Details, combined and unresolved Type values, Close/Back, extreme names and root controls. Final normal/large/extreme Info captures were inspected in both variants: the full Copy suffix is drawn, controls remain unobscured and extreme names ellipsize cleanly. Cropped Dashboard and Settings headers have zero differing pixels against the pre-fix private controls. Exact original preferences, photo bytes, network and font settings were restored; the crash buffer is empty.

Final evidence is `.amp/in/artifacts/type-heading-integrated/{build-validation.json,public/result.json,private/result.json,render-review.json}` with build/native/UI logs and captures alongside. The full five body/photo/custom/workout/media flows belong to the earlier unchanged-APK checkpoint in `combined-duplicate-photo/`, not to this focused presentation rerun. Public APK assets remain limited to three catalog JSON files. No phone operation or publication ran.
