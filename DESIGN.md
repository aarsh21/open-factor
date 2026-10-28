---
name: Open Factor
description: Simple native workout logging in charcoal and white, with Nuvio navigation and grouped settings.
colors:
  primary: "#FFFFFF"
  on-primary: "#000000"
  background: "#131313"
  surface: "#202020"
  elevated: "#353535"
  muted: "#B8B8BC"
  line: "#414141"
  blue: "#3982FF"
  complete: "#91D8AD"
  purple: "#B794F4"
  error: "#FF9696"
  light-primary: "#202020"
  light-on-primary: "#FFFFFF"
  light-background: "#F5F5F5"
  light-surface: "#FFFFFF"
  light-elevated: "#E5E5E7"
  light-muted: "#55555B"
  light-error: "#B3261E"
  navigation: "#1C1C1E"
  navigation-selected: "#393939"
  navigation-icon-muted: "#92969C"
  navigation-label-muted: "#A1A4AA"
  navigation-rim-top: "#9B786E"
  navigation-rim-bottom: "#2E2929"
  settings-background: "#0D0D0D"
  settings-group: "#1A1A1A"
  settings-border: "#272727"
  settings-divider: "#252525"
  settings-icon: "#343434"
  settings-muted: "#96999F"
typography:
  display:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "25sp"
    fontWeight: 700
  headline:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "40sp"
    fontWeight: 700
    lineHeight: "46sp"
    letterSpacing: "-1sp"
  title:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "20sp"
    fontWeight: 700
  body:
    fontFamily: "DM Sans, sans-serif"
    fontWeight: 400
  label:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "12sp"
    lineHeight: "16sp"
rounded:
  control: "8dp"
  metric: "10dp"
  settings-icon: "12dp"
  settings-group: "14dp"
  surface: "16dp"
  capsule: "50%"
spacing:
  micro: "4dp"
  tight: "6dp"
  compact: "8dp"
  inline: "10dp"
  small: "12dp"
  medium: "14dp"
  inset: "16dp"
  page: "18dp"
  section: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.control}"
  button-primary-light:
    backgroundColor: "{colors.light-primary}"
    textColor: "{colors.light-on-primary}"
    rounded: "{rounded.control}"
  button-outlined:
    textColor: "{colors.primary}"
  button-text:
    textColor: "{colors.primary}"
  round-action:
    backgroundColor: "{colors.elevated}"
    textColor: "{colors.primary}"
    rounded: "{rounded.capsule}"
    size: "48dp"
  input-search:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    rounded: "{rounded.capsule}"
  input-metric:
    textColor: "{colors.primary}"
    rounded: "{rounded.metric}"
  chip-selected:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    rounded: "{rounded.surface}"
    padding: "16dp"
  navigation:
    backgroundColor: "{colors.navigation}"
    rounded: "{rounded.capsule}"
    height: "66dp"
    padding: "4dp"
  navigation-selected:
    backgroundColor: "{colors.navigation-selected}"
    textColor: "{colors.primary}"
    rounded: "{rounded.capsule}"
    height: "58dp"
  settings-group:
    backgroundColor: "{colors.settings-group}"
    textColor: "{colors.primary}"
    rounded: "{rounded.settings-group}"
---

# Design System: Open Factor

## Overview

Open Factor is a simple native workout logger inspired by Strong and Fitbod. The current direction is a quiet charcoal interface with white actions, clear exercise names and aligned set values. Start on Workout, choose an exercise, enter weight and reps, add warmups, complete sets and finish into history. Page and section titles stand alone without explanatory captions.

The floating Nuvio navigation and grouped Settings remain part of the existing system. Programs, body data, analytics and advanced logging remain available, while the default journey stays short. The current direction at the top of [PRODUCT.md](PRODUCT.md) and the [workouts contract](.impeccable/surfaces/workouts.md) supersedes the older MacroFactor parity brief. Historical parity differences are not release blockers for this direction.

**Key characteristics:**

- Charcoal tonal grouping, white foreground and restrained completion/error colors.
- Publicly bundled DM Sans, native Compose controls and scalable type.
- Compact exercise chips, editable set rows and an always visible session Finish action.
- Device-local persistence, resumable sessions and optional workout/rest notifications.

This document extracts `Ui.kt`, `QuickStart.kt`, `WorkoutHome.kt`, `WorkoutSession.kt`, `MainActivity.kt`, `Exercises.kt` and `Settings.kt`. The final `inspection/release-evidence/phone-{onboarding,home,picker,logger,summary,history}.png` captures and `small-phone-large-type-{onboarding,logger}.png` describe the captured native states. The documentation pass inspected onboarding, home, picker, logger and enlarged-type logger images. Release validation supplied for this refresh records 114 unit tests per debug/release variant, 17 instrumentation tests on each of API 26 and API 35, signed minified release UI recording and process-death recovery checks. This pass did not run builds or device automation. No HTML/CSS detector ran on this native app. Physical-device performance, TalkBack operation and tablets are not certified by these checks; independent finish review is a separate artifact.

## Colors

Frontmatter records the dark palette and the explicit light substitutions in `OpenFactorTheme`. `OF.top` and `OF.card` resolve to the theme surface; `OF.elevated` resolves to surface variant. Dark is the default; Light and System are available.

- **Primary:** white on dark surfaces, black ink on the primary action. In light mode the primary action becomes charcoal with white text. Use theme `primary` and `onPrimary` together.
- **Neutrals:** base charcoal, surface charcoal and raised charcoal create page, header and control layers. Muted gray carries secondary values; separator gray carries outlines and lines. Light mode substitutes the `light-*` values for those theme roles.
- **State and training:** completion green marks completed controls and an 8% completed-row wash; validation rose is the dark error color, with the explicit light error substitution. Training blue and purple remain available to existing analytics/training surfaces. They do not replace the neutral logging actions.
- **Navigation:** the shell, selected capsule, inactive icon/label tones and two rim colors are scoped Nuvio values. Navigation remains dark in light mode.
- **Settings:** the `settings-*` values describe its dark treatment. In light mode Settings uses the theme background, card, elevated and muted roles instead.

## Typography

DM Sans is bundled publicly at `openfactor/app/src/main/assets/fonts/DM-Sans.ttf` under the OFL license. `Ui.kt` loads it at 400, 500 and 700; failed loading falls back to Android sans. Display headings also load this public file at 700. Private MacroSans is no longer the current display font.

- **Display:** root `PageHeading`, 25sp bold. Detail/back headings use 20sp bold. Shared headings allow three lines before ellipsis and leave room for actions.
- **Headline:** the frontmatter 40sp/46sp role with −1sp tracking describes the Settings title. Quick setup uses the Material `headlineLarge` role with DM Sans bold.
- **Title:** exercise/section headings use Material `titleLarge`; this mapping specifies the bold family and semibold weight. Primary-action and compact session titles use `titleMedium` with the medium family. Preserve these distinctions.
- **Body:** `bodyLarge` and `bodyMedium` use DM Sans regular, retaining Material default sizes, line heights and tracking. Numeric set fields center body-large text. Settings row titles explicitly use 17sp.
- **Label:** navigation uses 12sp/16sp, bold when selected and regular otherwise. Labels shrink to fit a fixed tab slot when required. Logger column labels explicitly use 12sp.

`OpenFactorTheme` maps selected Material roles rather than every role: `headlineSmall`, `bodySmall`, `labelSmall` and other untouched roles retain their library defaults. This is an implementation boundary, not a separate approved display family. No global uppercase transform or new type-scale ratio is specified.

## Layout

The application is Kotlin/Jetpack Compose. Geometry uses dp and text uses sp. Primary screens fill the viewport and scroll vertically; common gutters are 18dp. Cards commonly use 16dp padding, while `SectionTitle` adds 24dp above and 14dp below. The observed spacing is a mixed scale, not a rigid grid. No tablet rearrangement or web breakpoint system is implemented here.

The shared Scaffold supplies system-bar insets. Root content takes the top inset and continues to the bottom edge behind floating navigation; non-root routes keep the Scaffold bottom inset and omit the pill. Navigation independently takes the bottom inset. Scrollable end clearance keeps final rows reachable: Workout uses 112dp. Root Exercises retains a bottom search overlay above the pill with a transparent surrounding row and list end clearance; its search treatment is distinct from the workout picker.

Quick setup uses safe-drawing padding, 24dp content padding and a vertically scrolling column. Picker search is directly below its heading, followed by a horizontal filter row and results. Advanced gym/type/laterality/muscle options expand with Filters; Resistance and Favorites remain directly available. Picker IME padding applies to the viewport once, keeping search and results within the usable area.

The session is a full-height IME-aware column: compact header, timer row, horizontal exercise chips, optional rest strip and a vertically scrolling selected-exercise body. The header's Finish remains outside the scrolling set list. The list has 18dp content padding. Exercise chips have a 48dp minimum height and 240dp maximum width; Add exercise is in the same row. Set metrics use weighted columns rather than fixed field widths. `RoundAction` explicitly measures 48dp with a 24dp icon. These sizes do not certify every legacy target or every enlarged-type state.

## Elevation & Depth

Ordinary surfaces use tonal fills and restrained outlines, with transparent theme surface tint. The Nuvio shell alone adds native `graphicsLayer.shadowElevation`: 6dp at rest plus up to 3dp from scroll energy. Its 1dp vertical-gradient rim gives the shell an edge. These native elevation values are not CSS shadow measurements. Menus, dialogs, sheets, ripple and focus behavior remain Material defaults.

Navigation selection moves over 280ms with `FastOutSlowInEasing`; the shell responds over 180ms with the same easing. Selected icons scale to 1.04 over 180ms. Scroll energy affects shell translation by at most 2dp and width by at most 0.8%; the tab targets remain fixed. The root scroll-energy reset is 140ms. Android animation settings govern the native animation paths. Do not extend this scoped motion into a global animation system.

## Shapes

Shared cards use 16dp corners. `PrimaryAction` and exercise images use 8dp; numeric set fields use 10dp. `RoundAction`, search and navigation are capsules. Settings groups use 14dp corners and their 40dp icon wells use 12dp corners. Settings borders and inset separators measure 0.5dp. Native buttons and chips retain their Material shapes when no override is supplied; the logger Warmup/Add set controls are not the 8dp `PrimaryAction` variant.

## Components

### Quick setup and workout home

First run asks only for Kilograms or Pounds, then Start training. Completion saves the unit, disables RIR and exercise auto-next for this quick-start path, and opens Workout. The small “Saved on your device” line is a persistence status, not a title caption. The more extensive legacy onboarding source is retained separately; it does not define the current first-run journey.

Workout exposes Start workout or Resume workout first, a history header action, Programs and Templates. Starting opens exercise selection directly. A saved draft can be continued. Template cards and recent history are conditional on actual saved data. Do not add explanatory captions beneath headings.

### Buttons

`PrimaryAction` fills its width with a minimum 52dp height, 8dp corners and `titleMedium` text. It uses theme `primary`/`onPrimary`, including light mode. An optional 20dp leading icon has an 8dp text gap. Internal padding, disabled state, ripple and keyboard focus come from Material; no custom hover token is inferred.

`RoundAction` is a 48dp elevated capsule with a 24dp icon and a supplied accessible description. Outlined and text buttons are subordinate native variants. Warmup is outlined and Add set is filled-tonal, side by side at 48dp minimum height. The session's compact header Finish button is enabled after at least one completed set and opens confirmation before saving. It remains directly visible as well as available in the overflow menu.

### Search, filters and media

The picker places the capsule outlined search at the top with contextual Clear search and Done actions. Filters reveal optional controls; active selections stay visible. Root Exercises retains its separate bottom search overlay. Do not copy that root placement into the workout picker.

Exercise images use 8dp corners. Public Everkinetic previews have a white backing and preserve the artwork with Fit scaling; absent art uses a consistent fitness icon on the elevated surface. Public coverage is bounded to reviewed exercise-ID mappings. Public builds bundle licensed previews and DM Sans and exclude private `reference/**` assets; [ASSETS.md](docs/open-factor/ASSETS.md) and [LICENSED-MEDIA.md](docs/open-factor/LICENSED-MEDIA.md) record ownership and coverage. Illustrations are not videos or a claim of complete catalog art.

### Logger chips and set rows

A horizontal row of native selected chips switches the active exercise without replacing the set list with media tiles. Completed exercises can show a check; Add exercise follows the chips. The selected exercise body sits on the page background. Notes display when present, with pinned exercise notes distinct from session notes.

For weight/reps exercises, quick setup produces the simple weight-and-reps path with RIR hidden. Other exercise metric families still use their actual required fields; settings may enable RIR. Set rows contain the set-options label, aligned numeric fields and a 48dp completion button. Weight uses a decimal keyboard; reps use a number keyboard. Invalid values receive visible and semantic errors and cannot complete the set. Completed controls turn green with black checks and the row receives an 8% green wash. Warmups display W, stay distinct from working-set numbers and remain addable alongside Add set.

The timer row and optional rest strip remain above the scrolling fields. Rest exposes +30s and Skip. Overflow menus retain targets/rest, notes, set kinds, supersets and other advanced choices. Finish confirms the completed-set count, optionally saves a template and opens the saved workout summary/history journey.

### Navigation and grouped Settings

The Nuvio capsule has Dashboard, Workout, central plus, Exercises and Settings in five fixed slots. Workout is the initial destination. The plus starts and opens the picker, or resumes an active session. The shell is 66dp high; its selected surface is 58dp with 4dp inset. Slot width is `(available width - 8dp) / 5`. The central button is 48dp. Shell width caps at 480dp with 12dp side padding and a 14dp bottom gap. Icons measure 26dp; destination controls use native tab selection semantics. Only the shell material responds to scroll; touch targets keep their anchor.

Settings retains the large title, grouped rows, monochrome icon wells and inset separators. Rows use 16dp horizontal/15dp vertical padding with a 15dp gap, 40dp wells and 25dp icons. The divider begins at 71dp. Row titles stand alone; the retained subtitle parameter is not rendered. Light mode substitutes theme surfaces. Existing Dashboard analytics, body-data editors, programs, history and other advanced routes retain their existing components; this refresh does not certify their visual parity or recast them as the default logging flow.

### Continuity and notifications

Active sessions are saved durably after changes and can be resumed after process recreation. Storage-recovery feedback and export preserve access to recoverable data rather than silently claiming a successful fresh load. Notifications are optional; on Android 13+ the app requests permission once an active workout exists, and denied permission does not block logging. Workout/rest notifications reopen the active session. Precise background timers depend on Android special access; force-stop suppresses alarms until reopening. These are state and recovery behaviors, not guarantees of uninterrupted delivery on every device.

## Do's and Don'ts

- **Do** keep the default journey focused on exercise selection, set entry, warmups and finishing.
- **Do** pair theme primary and on-primary colors and use the bundled public DM Sans.
- **Do** preserve visible Finish, top picker search, compact exercise chips and native IME handling.
- **Do** retain system insets, scroll end clearance, labeled actions and durable session state.
- **Do** keep legacy advanced routes and licensed-media coverage accurately scoped.
- **Don't** add explanatory captions beneath page or section titles.
- **Don't** spread the navigation rim gradient or Settings-specific palette across logging content.
- **Don't** restore exact MacroFactor parity as the current release criterion.
- **Don't** substitute private recovered assets, invented media or claimed videos for missing public art.
- **Don't** infer TalkBack, physical performance, tablet or universal large-font certification from source or screenshots.
