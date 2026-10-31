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
  navigation-rim-top: "#414143"
  navigation-rim-bottom: "#414143"
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

Open Factor makes meaningful training features easy to use. The current direction pairs a quiet charcoal logger with original exercise photos, videos, instructions and anatomy. Guidance is directly reachable from discovery and the active set; it is a core capability rather than decoration. Start on Workout, choose an exercise, view its guide when needed, enter weight and reps, add warmups and finish into history. Page and section titles stand alone without explanatory captions.

The floating Nuvio navigation and grouped Settings remain part of the existing system. Programs, body data, analytics and advanced logging remain available; the active program is visible on Workout and the default journey stays short. The current direction at the top of [PRODUCT.md](PRODUCT.md) and the [workouts contract](.impeccable/surfaces/workouts.md) supersedes the older MacroFactor parity brief. Historical parity differences are not release blockers for this direction.

**Key characteristics:**

- Charcoal tonal grouping, white foreground and restrained completion/error colors.
- Publicly bundled DM Sans, native Compose controls and scalable type.
- Original media, selectable human anatomy, compact exercise chips and visible Finish.
- Device-local training, bundled static guidance, visible-guide muted playback over HTTPS and a bounded cache.

The 0.6.0 behavior update follows `QuickStart.kt`, `ProfileSetup.kt`, `ProfileEditor.kt`, `Settings.kt`, `ExerciseMedia.kt`, `ExercisePlaybackState.kt` and the catalog/search repositories. The [0.6.0 release evidence](docs/open-factor/releases/v0.6.0.md) records builds, tests, actual-provider import, playback and startup observations. Its native visual review covers an API35 phone in dark theme at normal and 1.35 font scale. Earlier captures remain evidence of their own checkpoints. This documentation pass did not run builds, device automation or an HTML/CSS detector. Physical-device performance, TalkBack operation, tablets, light theme and other locales remain outside that visual review.

The 0.6.1 extension follows `TemplateScreens.kt`, `TemplateData.kt`, `MainActivity.kt`, `HealthConnectPanel.kt`, `HealthConnectSyncCommitter.kt`, `Data.kt` and `ScopedDataExport.kt`. It preserves the recorded tokens, sidecar, palette, type and navigation motion. The [0.6.1 release evidence](docs/open-factor/releases/v0.6.1.md) and `inspection/release-evidence/v0.6.1/finish-review.md` record a ship disposition for candidate captures 01–19 on an API35 phone in dark theme at normal and 1.35 font scale. The finish review's sampled source and native captures establish this extension's checkpoint; they do not extend the earlier review's device or accessibility scope.

## Working-tree dashboard and import update

Dashboard customization now groups ordered tiles into compact section cards with one Edit action, a selected count, and contextual reorder/remove menus. Choice sheets use one checkbox action per row; exercise search runs off the UI thread. Draft changes still apply only on Save. The Workout home weekly card uses one prominent working-set count, an explicit date range, secondary session/muscle totals and a whole-card History action—no invented progress ring or percentage. The charcoal/light surfaces, DM Sans, 18dp page gutter, 16dp card shape and floating navigation are unchanged.

Spreadsheet import uses the native document picker and a preview with explicit exercise choices, set count, omissions and confirmation. Its pinned actions reserve clearance for floating navigation and system bars. The [performance/verification record](docs/open-factor/PERFORMANCE.md) distinguishes final native checks and large synthetic data from physical-device/frame-time claims; [spreadsheet scope](docs/open-factor/SPREADSHEET-IMPORT.md) records partial-target limitations. These working-tree changes do not extend the published 0.6.2 release evidence.

## Exercise preview framing

Compact exercise previews fill their existing rounded frames with a centered crop, rather than fitting a narrow portrait inside a wider slot and exposing side gutters. The shared `ExerciseImage` applies this consistently to the exercise library, logger, workout/template composers, program editor, template lists and history summaries. Loading and missing photos use the theme's neutral raised surface, with an accessible exercise icon on failure. Transparent licensed line illustrations retain a white canvas for contrast. Full demonstration photos/videos keep their natural-aspect presentation; this thumbnail change does not crop the instructional guide or modify any source media.

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

Quick setup uses safe-drawing and IME padding, a persistent Back/Skip header, three horizontal progress segments, a vertically scrolling form with 24dp content padding, and an anchored primary action. Profile uses a persistent Back/Save heading above its scrolling fields. Picker search is directly below its heading, followed by a horizontal filter row and results. Advanced gym/type/laterality/muscle options expand with Filters; Resistance and Favorites remain directly available. Picker IME padding applies to the viewport once, keeping search and results within the usable area.

The session is a full-height IME-aware column: compact header, timer row, horizontal exercise chips, optional rest strip and a vertically scrolling selected-exercise body. The header's Finish remains outside the scrolling set list. The list has 18dp content padding. Exercise chips have a 48dp minimum height and 240dp maximum width; Add exercise is in the same row. Set metrics use weighted columns rather than fixed field widths. `RoundAction` explicitly measures 48dp with a 24dp icon. These sizes do not certify every legacy target or every enlarged-type state.

## Elevation & Depth

Ordinary surfaces use tonal fills and restrained outlines, with transparent theme surface tint. The Nuvio shell alone adds native `graphicsLayer.shadowElevation`: 6dp at rest plus up to 3dp from scroll energy. Its 1dp vertical-gradient rim gives the shell an edge. These native elevation values are not CSS shadow measurements. Menus, dialogs, sheets, ripple and focus behavior remain Material defaults.

Navigation selection moves over 280ms with `FastOutSlowInEasing`; the shell responds over 180ms with the same easing. Selected icons scale to 1.04 over 180ms. Scroll energy affects shell translation by at most 2dp and width by at most 0.8%; the tab targets remain fixed. The root scroll-energy reset is 140ms. Android animation settings govern the native animation paths.

Screen changes use one depth-aware transition (`AppNavigation.kt`). Deeper routes push in from 1/12 of the width with a 220ms fade while the departing screen drifts 1/24 left and fades in 120ms; shallower routes reverse it. Tabs and same-depth swaps fade through in place without sliding. The floating navigation rides the same transition, sinking away under pushed screens. Android 14+ predictive back scrubs the pop transition with the gesture (linear, so half a swipe is half the travel) and cancelling returns the screen; programs keep their discard confirmation. In a workout, the rest banner unfolds, newly added sets settle in from 8dp above while existing and restored sets stay still, and switching exercises fades without sliding. User-ordered lists animate reorder and removal; search results update instantly. With Remove animations, screens swap without overlapping and nothing moves. `NavigationMotionTest` measures these at fixed clock times. The workout keypad enters by sliding up its own height with a fade over `AppMotion.Step` (220ms) `EaseOut` and leaves over `AppMotion.Quick` (120ms); moving focus between fields never replays it, key presses use only the shared 0.975 press feedback plus a keyboard haptic tick, values change instantly, and Remove animations makes it appear and disappear without motion.

## Shapes

Shared cards use 16dp corners. `PrimaryAction` and exercise images use 8dp; numeric set fields use 10dp. `RoundAction`, search and navigation are capsules. Settings groups use 14dp corners and their 40dp icon wells use 12dp corners. Settings borders and inset separators measure 0.5dp. Native buttons and chips retain their Material shapes when no override is supplied; the logger Warmup/Add set controls are not the 8dp `PrimaryAction` variant.

## Components

### Quick setup and workout home

First run uses three focused pages: “Make it yours” collects a required name and optional gender; “Your starting point” offers direct Health Connect plus optional weight/height and unit choices; “Ready, set, log.” collects weekly goal, effort rating and automatic rest/duration. Continue advances and Start training validates and saves the profile before opening Workout. Back preserves values; Skip for now opens Workout with the raw draft and current step retained. Setup progress survives restart. Existing completed installations are not forced through it again.

The setup Health Connect action returns once to the starting-point form only after a successful import is saved. Denial, read/provider failure or save failure leaves feedback and retry in the panel; manual Settings sync stays there after success. Native consent alone and an existing Connected state do not navigate.

Profile reuses the same fields in one scrolling editor with explicit Save, validation and discard confirmation. Review setup reopens the staged flow. Imported weight can refresh an untouched baseline while preserving its original date; typed input wins, and an unchanged imported or historical value is not saved as a new manual reading. These are local profile controls, not account creation or a return to the historical gym/program setup rail.

Workout exposes Start workout or Resume workout first, a history header action, Programs and Templates. Starting opens exercise selection directly. A saved draft can be continued. The active program shows its name and up to four workout-day links when present. Template cards and recent history are conditional on actual saved data. Do not add explanatory captions beneath headings.

### Templates and starter splits

Templates uses native Your templates / Starter splits segments and seven ordered day cells. The four starter schedules are Upper / Lower / Push / Pull / Legs, Upper / Lower, Bro split and Full body; their exact workout/rest order is recorded in [PRODUCT.md](PRODUCT.md). A selected day shows its title, original target-muscle artwork in a horizontal row, exercise photos, counts and target summaries. Use split stays pinned below the scrolling preview and saves independent editable day copies. Saved workout days retain an Edit action and pinned Start named for the selected day; Rest is an explicit empty slot without Start. Compact selectable strips scroll horizontally when the available width cannot fit seven usable cells.

The full-width template composer reuses the shared heading, tonal exercise rows, outlined fields and anchored Save template action. Target muscles sit above the exercise count; each row has its original photo, title, target summary, target controls and reorder/remove menu. Expanded targets edit sets, supported rep ranges and rest seconds. Raw input survives recreation; invalid values block Save and changed input has discard protection. Add exercises opens the shared top-search picker with multi-selection, muscle shortcuts, Resistance, Favorites and expanded equipment/gym, type, laterality and muscle filters. Exercise titles open the existing guide, returning to the retained selection. A new workout keeps prescriptions visibly separate from blank performed values, and an active workout blocks another template start. This extension introduces no new visual tokens or motion rules.

### Buttons

`PrimaryAction` fills its width with a minimum 52dp height, 8dp corners and `titleMedium` text. It uses theme `primary`/`onPrimary`, including light mode. An optional 20dp leading icon has an 8dp text gap. Internal padding, disabled state, ripple and keyboard focus come from Material; no custom hover token is inferred.

`RoundAction` is a 48dp elevated capsule with a 24dp icon and a supplied accessible description. Outlined and text buttons are subordinate native variants. Warmup is outlined and Add set is filled-tonal, side by side at 48dp minimum height. The session's compact header Finish button is enabled after at least one completed set and opens confirmation before saving. It remains directly visible as well as available in the overflow menu.

### Search, filters and media

The picker places the capsule outlined search at the top with contextual Clear search and Done actions. Filters reveal optional controls; active selections stay visible. Root Exercises retains its separate bottom search overlay. Do not copy that root placement into the workout picker. The catalog/equipment snapshot loads once per process on a worker thread; shared search indexes preserve default ordering across visits, with at most four catalog indexes retained and fresh indexing after catalog changes. This supports responsive navigation without changing search placement or ranking semantics.

Exercise thumbnails use 8dp corners. Static/full media modes preserve original exercise photos and available instructions, anatomy and equipment art; source mode uses the limited licensed Everkinetic previews or a consistent fitness icon. Everkinetic previews have a white backing and Fit scaling. These fallback assets do not define the intended original-media experience. [ASSETS.md](docs/open-factor/ASSETS.md), [MEDIA-PACKAGING.md](docs/open-factor/MEDIA-PACKAGING.md) and [LICENSED-MEDIA.md](docs/open-factor/LICENSED-MEDIA.md) distinguish content ownership, modes and coverage.

### Logger chips and set rows

A horizontal row of native selected chips switches the active exercise without replacing the set list with media tiles. Completed exercises can show a check; Add exercise follows the chips. The selected exercise body sits on the page background. Its photo, title and Guide action open rich exercise guidance in a sheet above the current workout. Closing the sheet returns to the same selected exercise and values. Notes display when present, ordered by reach: pin "Pinned · every workout", list "Template · name", pencil "This workout", then clock "Last time · date" only while today's note is empty.

Each note is a 16dp icon, a one-line `labelMedium` label and `bodyMedium` text clamped to three lines (two for last time); tapping a clamped note reveals it, tapping a full note edits it. Last time is muted (78% `onSurfaceVariant`), never copied, and starts the editor empty with its text as placeholder. Notes present when the exercise appears render still; a newly written note opens its space over 180ms and settles in with `arrival`. Last time tracks the finger 1:1 toward the reading end, with rubber-band friction the other way and multi-touch ignored after the drag starts. A 0.11 dp/ms flick or a 40% drag commits: it leaves at release speed while fading, then its space collapses over 180ms; anything else springs home (critically damped, slight bounce only after a fast reversal). The close button takes the same 180ms ease-out exit. With Remove animations the swipe only fades, and dismissal is instant. Dismissal persists on that workout exercise.

Weight/reps exercises use the simple weight-and-reps fields; other exercise metric families use their actual required fields. Setup, Profile and Workout logging settings control the effort prompt after exercise completion. Set rows contain the set-options label, aligned numeric fields and a 48dp completion button. They do not contain an inline RIR field. Log all sets validates all unfinished rows before completing them atomically, then one optional effort sheet records reps left for the last working log. Done/Skip is durable and Rate last set supports correction. Focusing a set field opens the workout keypad (Workout settings → Workout keypad, on by default; off restores the system decimal/number keyboard). Invalid values receive visible and semantic errors and cannot complete the set. Completed controls turn green with black checks and the row receives an 8% green wash. Warmups display W, stay distinct from working-set numbers and remain addable alongside Add set.

The workout keypad is a 16dp-top-corner `surface` panel below the set list, so the list shrinks and scrolls the focused field above it rather than being covered. A 4×4 grid of 10dp keys (54dp tall, 48dp on short screens, max 560dp wide) holds elevated digits, background-tone functions and a `primary` Log set key: 1–3 / +step, 4–6 / −step, 7–9 / Next, decimal (load and distance only) / 0 / delete / Log set, under a muted unit label and a Hide keypad action. Steps are 2.5 kg or 5 lb, 1 rep, 5 seconds, 0.1 km/mi and 10 m/yd, clamped to each metric's valid minimum; holding a step repeats after 400ms, accelerating from 160ms to 60ms. A focused value is shown selected and the first key replaces it; a step keeps the result selected. Next walks the row, then the first field of the next unfinished row (warm-ups, sides and Myo components included) and becomes Done at the end. Log set runs the row's own completion path. Back, Hide keypad and taps outside fields close it; scrolling does not. Keys never take input focus, and fields keep their descriptions, errors and SetText action.

The timer row and optional rest strip remain above the scrolling fields. Rest exposes +30s and Skip. Overflow menus retain targets/rest, notes, set kinds, supersets and other advanced choices. Finish confirms the completed-set count, optionally saves a template and opens the saved workout summary/history journey.

### Exercise swap

Swap exercise… opens a 95%-height Material sheet over the session: title, “Replacing …” line, a note when completed sets will stay behind, up to eight suggestion rows (8dp photo, two-line title, muscle · equipment, at most three capsule reason chips, trailing Guide) and a pinned outlined Search all exercises button. Guide cross-fades the same sheet to the shared exercise guide with a pinned Swap for … action, rather than stacking a second sheet. Results fade in once (Quick, EaseOut) without stagger; an empty result uses `EmptyContent` pointing to Search all. Template/program workouts confirm scope in a compact two-button dialog. After a commit the sheet slides away and the replacement block fades in place (State, EaseOut); all motion follows `LocalAppMotionEnabled`. A Long snackbar offers Undo.

### Exercise guide, video and anatomy

Discovery opens the exercise guide directly; exercise settings are a secondary header action. Guide, Muscles and History tabs keep media, anatomy and previous training accessible. The logger opens the same content in a closeable sheet rather than replacing its current set.

Original media fills the available width in a 9:16 portrait frame with Fit scaling; licensed preview fallback uses a square frame. The full frame scrolls naturally on short screens without added black side margins. The original photo remains the preview while video loads or is unavailable. A compact 1X/2.5X/5X, pause/play and replay capsule floats over the top, clear of the side camera rail; there is no seek bar. Camera thumbnails retain original view order.

A visible guide autoplays muted in the resumed app and loops one ordered playlist of available camera angles. Direct camera selection remains available. Scrolling the media offscreen, leaving the guide or backgrounding stops loading and releases the player; a deliberate pause remains paused on return. Errors keep the preview and show status with Retry immediately above the frame. Bundled clips take precedence; other clips use HTTPS and a 128 MiB least-recently-used cache. Cache storage is not a permanent offline library. These implemented paths do not certify server availability or every device codec.

The Muscles tab displays all targets in a noninteractive Primary/Secondary legend and original front/back figures side by side with simultaneous highlights. Male/Female remains the only segmented choice, with the saved profile supplying the initial figure. Preserve the original human figures and readable scale; generic fallback icons only describe unavailable source-mode assets.

### Navigation and grouped Settings

The Nuvio capsule has Dashboard, Workout, central plus, Exercises and Settings in five fixed slots. Workout is the initial destination. The plus starts and opens the picker, or resumes an active session. The shell is 66dp high; its selected surface is 58dp with 4dp inset. Slot width is `(available width - 8dp) / 5`. The central button is 48dp. Shell width caps at 480dp with 12dp side padding and a 14dp bottom gap. Icons measure 26dp; destination controls use native tab selection semantics. Only the shell material responds to scroll; touch targets keep their anchor.

Settings retains the large title, grouped rows, monochrome icon wells and inset separators. Rows use 16dp horizontal/15dp vertical padding with a 15dp gap, 40dp wells and 25dp icons. The divider begins at 71dp. Row titles stand alone; the retained subtitle parameter is not rendered. The five groups are You, Training, App, Data and About. Profile is the editable personal/training destination; Body data and Health Connect remain directly reachable. Workout logging retains effort rating, exercise advance, keep-screen-on and workout timer; Rest timer, equipment, library, history, units, shortcuts, appearance, backup and attribution remain useful destinations. Unavailable language/energy controls, the ineffective bodyweight-contribution toggle and the old setup ceremony are removed. Light mode substitutes theme surfaces. Existing Dashboard analytics, body-data editors, programs, history and other advanced routes retain their existing components; this refresh does not certify their visual parity or recast them as the default logging flow.

### Continuity and notifications

Active sessions are saved durably after changes and can be resumed after process recreation. Storage-recovery feedback and export preserve access to recoverable data rather than silently claiming a successful fresh load. Notifications are optional; on Android 13+ the app requests permission once an active workout exists, and denied permission does not block logging. Workout/rest notifications reopen the active session. Precise background timers depend on Android special access; force-stop suppresses alarms until reopening. These are state and recovery behaviors, not guarantees of uninterrupted delivery on every device.

## Do's and Don'ts

- **Do** keep the default journey focused on exercise selection, set entry, warmups and finishing.
- **Do** pair theme primary and on-primary colors and use the bundled public DM Sans.
- **Do** preserve visible Finish, top picker search, compact exercise chips and native IME handling.
- **Do** retain system insets, scroll end clearance, labeled actions and durable session state.
- **Do** keep original guidance prominent, keep active programs visible, and scope media coverage and build modes accurately.
- **Don't** add explanatory captions beneath page or section titles.
- **Don't** spread the navigation rim gradient or Settings-specific palette across logging content.
- **Don't** restore exact MacroFactor parity as the current release criterion.
- **Don't** replace the intended original guidance with generic icons or confuse source-only fallback with a full content pack.
- **Don't** infer TalkBack, physical performance, tablet or universal large-font certification from source or screenshots.

## Review and correction in 0.5.2

Exercise history shares the recorded-set table with workout summaries, filling the content width and retaining actual metric families, warmups, unilateral/Myo components and RIR. Its dated workout header opens the saved workout. Edit workout opens an isolated durable draft, with explicit Save and discard confirmation; it never reuses the active workout. Name, date, notes, actuals, completion and effort are editable; set/exercise additions and removals remain reversible until Save. Invalid inputs block Save; stale saved records cannot be overwritten.

Dashboard uses Week/Month segments and calendar period arrows above its three counters; the repeated training bar chart is removed. Workload and its drilldown use the same selected range. Existing steps graphs are unchanged.
