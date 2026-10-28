# Simple logger 0.2.0 review

disposition: fix

## Disposition

Fix the two small but visible consistency defects below, then recapture the summary and history from the final signed artifact. No redesign or rebuild is indicated. This review follows the October 2026 simple-logger direction; historical MacroFactor parity is outside its acceptance scope.

## Material findings

| Before | After | Why |
| --- | --- | --- |
| Logger shows `W, 1`; summary shows `Warm-up, Set 2`. `WorkoutSession.kt:880` numbers all logical groups, while the logger at line 351 counts working groups. SetRow descriptions at lines 647, 696, 720 and 743 also announce the physical group index. Reproduce: add a warmup before the first working set, complete both, finish. | Use the same working-set number throughout logger, summary and accessibility descriptions; announce warmups explicitly. | P2: the saved result and spoken controls identify a different set from the visible logger. Both supplied summary and source confirm this defect. |
| History reads `1 exercises · 2 sets` (`WorkoutSession.kt:798`; `phone-history.png`). | Select singular/plural forms for exercise and set counts. | P3: the first completed workout exposes an avoidable copy defect on a core destination. |

No material clipping, lost core action or obvious text-contrast failure appears in the nine required captures. The 1.3-font logger keeps Finish, weight/reps, completion, Warmup and Add set visible. The picker list's bottom cutoff is normal scrolling content, not clipping of a fixed control.

## Evidence checked

Opened the exact nine files under `inspection/release-evidence/`: `phone-onboarding.png`, `phone-home.png`, `phone-picker.png`, `phone-logger.png`, `phone-summary.png`, `phone-history.png`, `phone-recovered-workout.png`, `small-phone-large-type-onboarding.png`, and `small-phone-large-type-logger.png`. The API35 phone captures are 1080×2400 at default type; API26 captures are 1080×1920 at 1.3 type. They show native app content matching their names. The packet attributes them to signed R8 APK SHA-256 `e1b024f7667543ca88f2f6fe7b383d81a28d8af0d3b2fa3c76b6c564cb613ece`.

Read PRODUCT.md, the current surface contract, craft-floor.md, android.md and the requested primary UI sources. No generated comp, QUALITY BAR card or HTML/CSS detector applies to the inherited native direction.

| Element | Verdict | Evidence |
| --- | --- | --- |
| TYPE | Match | Bundled DM Sans, clear title/body hierarchy, enlarged type remains readable. |
| MATERIAL | Match | Flat charcoal native surfaces, familiar icons and Material controls; no imitation physical texture. |
| GROUND | Match | Neutral charcoal fields and white primary actions follow the current contract. |
| Default journey | Match | One unit choice, Start workout, top search, weight/reps and separate Warmup/Add set actions; no explanatory title captions. |
| Saved-result identity | Contradicted | Summary and semantic set numbering diverge from the logger after adding a warmup. |

The nested `api26/results/debug/TEST-*.xml` and `api35/results/debug/TEST-*.xml` each record 17 tests with zero failures/errors. The top-level XML files are older 14-test runs. `process-death.json` records the killed background process and surviving ongoing notification; the recovered screenshot retains values and rest timing. `release-frame-sample.json` reports 22 frames and 13 missed deadlines on the API35 emulator. No builds, tests, device automation or source edits were performed for this review.

## Unverified boundaries

Actual visible Gboard/IME behavior remains unverified: the enlarged logger capture has a focused field but no keyboard. Source has viewport IME padding, which does not establish usable keyboard-visible rendering. Spoken TalkBack behavior, physical-device gesture/refresh/performance behavior, and notification permission/precise-alarm paths were not independently exercised. The packet reports 114 passing unit tests per variant and a recorded signed-release journey; this review did not rerun those tests or play the video. Emulator frame evidence does not certify universally smooth performance.

## Handoff

Keep the sparse native logging flow, charcoal/white hierarchy, prominent Finish, persistent active workout and unobstructed set actions. Apply the two findings, recapture affected screens with the final APK hash, and retain keyboard/notification/hardware boundaries in the release evidence unless separately verified.

## Verdict addendum — two-finding scope

Opened `phone-summary-final.png`, `phone-history-final.png`, `phone-logger-final.png` and `phone-recovered-workout-final.png` at their exact supplied paths under `inspection/release-evidence/`. This scores only the two original findings against the packet's final signed APK SHA-256 `1456bc4673b75d589ef246e22926e9a6c3e784d6e10e29c44ac25580d393657b`, code commit `28b067a99bb4090880ec59802e11a267336a775f`.

| Finding | Verdict | Evidence |
| --- | --- | --- |
| Working-set identity after warmups | Resolved | Final logger and recovered logger show `W, 1, 2`; final summary now shows `Warm-up` and `Set 1 · Standard`. `WorkoutSession.kt:350` supplies separate warmup/working ordinals; lines 633, 648, 697, 721 and 743–744 distinguish warmups in semantic labels; line 882 counts nonwarmup groups for summary numbering. |
| Singular/plural history copy | Resolved | Final history reads `1 exercise · 2 sets`; `WorkoutSession.kt:799` selects both exercise and set singular/plural forms. |

No regression introduced by this fix batch is visible in these four captures. `QuickWorkoutJourneyTest.kt:94` asserts `Set 1 · Standard`; `verified-results/api26.xml` and `verified-results/api35.xml` each report 17 tests with zero failures/errors. These files were inspected without rerunning tests. The original keyboard, spoken-accessibility and hardware-performance boundaries remain unchanged.

Remaining: clear for the two scored findings. This is a ship disposition at fix-list scope only, not a new whole-app audit.

disposition: ship
