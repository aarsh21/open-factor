# Onboarding and floating navigation

Mode: Operate. Platform: Android. Artifact: openfactor/app/.

## THESIS

First launch follows the captured Workouts welcome and three-stage Basics → Gym & Equipment → Program setup. Setup resumes after restart, can be deferred, and can be reopened from Settings. Existing workouts and saved programs survive upgrade and replay.

## OWN-WORLD

Keep the Workouts charcoal field, centered large headings, numbered vertical stage rail and anchored white actions. Brand the welcome Open Factor. Use the existing font provenance/fallback rules. Source account sign-in and health sync must not be represented as working local features. The exact intermediate Basics pages were not recorded; their local profile form is a reconstruction, not verified source parity.

## STORY

Welcome → setup overview → optional sync → local profile/units → gym stage and profile selection → program stage → existing manual-program setup or defer to dashboard. Keep values and current step through process death. Existing installations enter their dashboard and can replay setup in Settings. Smart generation and XLSX stay explicitly unavailable.

## FIRST VIEWPORT

Source authority: private workouts-app.png and workouts-started.png, with workouts-menu.xml/workouts-started.xml establishing the optional Health Connect step. The screenshot and XML timestamps differ; do not treat them as a synchronized pair. The overview shows three numbered stages and Go to Basics. No new raster comp applies to this pinned source reproduction.

## FORM

Native Compose; scrollable content, system/IME insets and fixed primary action. Navigation overlays root scrolling content with trailing scroll clearance. The user clarified that all space behind and around the capsule must show the page: root content draws to the bottom edge, and only the capsule/search field paint their own shapes; no full-width navigation backing strip or reserved bottom viewport. Search floats above the capsule while the list continues beneath both. Navigation keeps the same bottom anchor and fixed tab targets, animates the shared selection and gently reacts to vertical root scrolling. No auto-hide, looping motion or added dependency. Respect Android Remove animations; verify on API26/API35 with a short motion recording, including interruption/tab changes and final-row reachability.
