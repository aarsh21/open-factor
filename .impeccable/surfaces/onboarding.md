# Onboarding and floating navigation

Mode: Operate. Platform: Android. Artifact: openfactor/app/.

## Current direction — 29 October 2026

The 0.6.0 user direction supersedes the welcome/numbered rail and units-only setup. Preserve the charcoal/white identity, bundled DM Sans and existing native controls; no new visual world or raster comp applies.

## THESIS

Make a useful personal and training profile in three short steps, with direct optional Health Connect. A required name completes setup; gender, weight and height are optional. Skip remains available, retains the raw draft and current step, and opens Workout. Existing completed installations keep their data and bypass first run.

## OWN-WORLD

Use large left-aligned DM Sans headings, sparse tonal fields, outlined inputs, native choice chips/switches and anchored white actions. Three thin horizontal progress segments describe the real flow. Titles stand alone. Keep original exercise media and the Nuvio floating navigation/grouped Settings elsewhere in the app; the historical vertical rail is not the current first-run pattern.

## STORY

“Make it yours” → “Your starting point” → “Ready, set, log.” → Workout. The pages collect name/optional gender; optional weight/height and their units; then weekly workout goal, effort rating, automatic rest and duration. The baseline page's Connect action opens Android Health Connect consent and automatically imports granted data. Manual entry and Continue remain available. Imported weight can fill an untouched baseline with its original date; typing wins and an unchanged older reading never becomes a fabricated weigh-in today.

Back keeps values and returns one stage; Back/Close at the first stage and Skip defer setup. Draft values and stage survive restart. Settings → Profile reuses the fields with validation, explicit Save and discard protection; Review setup returns to the staged flow. Weekly goals feed Habits, and saved gender supplies the initial anatomy figure. Setup does not require gym/program creation or an account.

## FIRST VIEWPORT

Show the Open Factor header with Back/Close and Skip for now, three horizontal progress segments, “Make it yours”, Name, optional gender choices and anchored Continue. Continue is disabled until the name is valid. The fourteen native PNGs in `inspection/release-evidence/v0.6.0/candidate-api35/` and the [0.6.0 release record](../../docs/open-factor/releases/v0.6.0.md) establish the current checkpoint. The reviewer inspected the API35 phone in dark theme at normal and 1.35 font scale; these captures do not certify all devices, themes, locales or accessibility operation.

## FORM

Native Compose with safe-drawing/IME padding, a persistent header, scrollable form and fixed primary action. Changing pages returns the form to its top. Profile has a persistent Back/Save heading above a scrolling editor. Unit switches convert valid values rather than reinterpreting them; invalid nonempty body input blocks progression/save. Rest duration appears when automatic rest is enabled. No new palette, typography scale, navigation geometry or global motion rule is introduced.

## Historical onboarding contract

The sections below retain the earlier source-reproduction brief and navigation investigation. Their welcome, vertical rail, gym/program sequence and dashboard entry do not describe the current setup; the current direction above governs. The floating-navigation requirements remain part of the incumbent system.

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
