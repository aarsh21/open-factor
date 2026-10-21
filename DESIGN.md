---
name: Open Factor
description: Inherited Workouts charcoal system with Nuvio navigation and grouped settings.
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
  error: "#FF9696"
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
    fontFamily: "MacroSans, sans-serif"
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
  button-outlined:
    textColor: "{colors.primary}"
    rounded: "{rounded.control}"
  button-text:
    textColor: "{colors.primary}"
  input-search:
    textColor: "{colors.primary}"
    rounded: "{rounded.capsule}"
  input-metric:
    textColor: "{colors.primary}"
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

**Creative North Star: "Source Workouts with Nuvio navigation and settings"**

Preserve the inherited Workouts visual hierarchy: neutral charcoal fields, white foreground, wide geometric workout headings, DM Sans reading text, blue training diagrams and real exercise photography. The interface serves recording and reviewing training. Density comes from aligned metrics and clear groups, rather than ornamental content.

The user's Nuvio exceptions are the floating navigation capsule and grouped Settings presentation; the user also explicitly requested the new onboarding and subtle scroll/tab response at a fixed navigation anchor. Their warmer navigation rim, moving selected surface, oversized Settings title and monochrome icon tiles belong to those components. They do not replace the workout world's neutral surfaces. Qualitative direction is already pinned in [PRODUCT.md](PRODUCT.md) and the [workouts contract](.impeccable/surfaces/workouts.md); this document records reused implementation rules, not a redesign or complete source acceptance.

Evidence for this documentation refresh is local source inspection of `openfactor/app/src/main/java/org/openfactor/app/Onboarding.kt`, `Ui.kt`, `MainActivity.kt`, `Data.kt`, `Dashboard.kt`, `Exercises.kt` and `Settings.kt`, plus `PRODUCT.md`, both surface contracts and `docs/open-factor/ONBOARDING.md`. The supplied checkpoint is app correction `0a12a431` after `51e19fdb`; revision identity was supplied by the parent, not independently checked here. The parent reports 26 unit tests, build/lint, and combined native setup plus resumed-navigation checks on API 26/API 35. These are reported checks, not runs or visual inspections performed by this documentation pass. Earlier logger/dashboard/settings captures remain evidence only for their captured checkpoint. No runtime, device, font-scaling, IME or TalkBack verification was performed here. Independent review is separate and pending; neither these checks nor this document establish full source parity or clone acceptance. A subsequent source-only recheck confirms the root Exercises search footer now adds bottom clearance (88dp) when `onBack == null`; picker/detail behavior is unchanged. Tests remain parent-owned and ongoing; this recheck adds no runtime verification or parity acceptance.

**Key Characteristics:**
- Neutral charcoal hierarchy with white primary actions and restrained semantic color.
- Geometric workout headings and DM Sans text when the local reference pack is present.
- Flat tonal grouping with rounded controls and a component-specific Nuvio capsule.
- Native selection, safe-area handling and persistent training state.

## Colors

The palette is achromatic at rest, with blue for training information and semantic colors for completion and errors. Frontmatter values are normative; identical header/card colors share `surface` because `OF.top` and `OF.card` are equal.

### Primary

- **White foreground** (`primary`): primary text, active selection, primary button fill and selected dashboard scope.
- **Black ink** (`on-primary`): text and icons on white primary actions and completed-set controls.

### Secondary

- **Training blue** (`blue`): weekly progress arcs and muscle/Levels information; it is the theme's secondary, not the default action fill.
- **Completion green** (`complete`): completed set check control. The logger also applies an eight-percent green wash to the completed row; that local row treatment is not a universal container style.
- **Validation rose** (`error`): theme error foreground for invalid fields. These state colors do not establish extra decorative accents.

### Neutral

- **Base charcoal** (`background`), **surface charcoal** (`surface`) and **raised charcoal** (`elevated`): page, header/card and control/rest-region layering.
- **Muted gray** (`muted`) and **separator gray** (`line`): supporting copy, field outlines and dividers. Do not use muted text to hide primary values.
- **Nuvio navigation neutrals** (`navigation`, `navigation-selected`, icon/label muted tokens): shell, moving selection and inactive destinations. The two `navigation-rim-*` colors form the observed vertical border gradient; their warmth stays in that rim.
- **Nuvio settings neutrals** (`settings-*`): darker page, grouped rows, faint group border/divider, icon tile and supporting labels. These tokens are scoped to Settings.

**The Neutral Foreground Rule.** Primary workout lettering and primary actions stay white; blue communicates training information and green communicates completion.

## Typography

**Display Font:** locally imported MacroSans variable, weight 700, for root workout page headings; runtime fallback is Android `FontFamily.SansSerif`.

**Body Font:** locally imported DM Sans variable, explicitly loaded at weights 400, 500 and 700; runtime fallback is Android `FontFamily.SansSerif`.

**Character:** broad geometric workout headings sit above plain, readable training labels and figures. Settings uses the larger DM Sans title as the Nuvio exception. The family stacks in frontmatter express the native load/fallback sequence; they do not mean web fonts are shipped.

### Hierarchy

- **Display:** root `PageHeading` uses the frontmatter display role. Setup locally reuses `workoutDisplayFont()` at (32sp), bold, with line height (36sp) and centered alignment; this does not change the global display role. Local MacroSans produces the uppercase geometric appearance from title-case strings; code does not globally uppercase text.
- **Headline:** frontmatter headline describes the explicit Settings title, not every Material headline. Workout summaries and other text still use named Material typography roles.
- **Title:** detail/back headings use the explicit frontmatter title role. Shared section headings use Material `titleLarge` with the imported bold family and a declared semibold weight; exercise titles and primary actions use `titleMedium` with the medium family. Keep those roles distinct.
- **Body:** `bodyLarge` and `bodyMedium` are mapped to the imported regular family; their sizes, line heights and tracking remain inherited from the app's Material 3 defaults. No new global body ramp or scale ratio is established here. Settings rows explicitly use titles (17sp) and subtitles (14sp, line height 21sp).
- **Label:** the frontmatter label size and line height describe navigation; selected labels are bold and inactive labels regular. Workout supporting labels commonly use 12sp, but do not all establish a 16sp line height. Weekly ring labels explicitly use bold outer labels (18sp) and a stronger center label (20sp); values use bold outer (24sp) and center (30sp) sizes.

**The Font Provenance Rule.** Local reference fonts describe the inspected appearance; the public source-only build falls back to Android sans and is not font-equivalent to the source.

Fonts live under `openfactor/app/src/main/assets/reference/fonts/`; the local importer and provenance are described in [ASSETS.md](docs/open-factor/ASSETS.md). The reference pack is ignored and its ownership is separate from GPL application code. No downloadable font, public font bundle or redistribution permission is implied. `headlineSmall`, `bodySmall`, `labelSmall` and other untouched roles are not comprehensively remapped by `OpenFactorTheme`; their system-font leakage is implementation drift, not a second approved type system. Exact fallback display typography remains unresolved rather than canonized as a system display face.

## Layout

Native Compose uses density-independent geometry (`dp`) and scalable type (`sp`); no web breakpoint system was found. Primary screens fill the available width and scroll vertically. Common page gutters are `page`; cards commonly use `inset` or `page` padding, and gaps draw from the compact through medium steps. `SectionTitle` separates groups with section space above and medium space below. Do not infer a rigid four-point grid from these mixed observed values.

The app Scaffold supplies system-bar insets; it does not reserve the pill's measured height. On primary destinations a bottom-aligned capsule overlays the root content, with transparent surroundings. Root scrolling content supplies trailing clearance: Dashboard and Workout (108dp), the Exercises list (104dp), and Settings (112dp). Separately, the Exercises search footer adds bottom clearance (88dp) only at the root (`onBack == null`), keeping search above the overlaid pill; picker footers retain no added bottom clearance, and detail routes omit the pill. The capsule retains horizontal margins (28dp), bottom gap (14dp), and maximum width (440dp), inside the system-inset-aware root. These are navigation constraints, not a global content width. Setup uses its own dark Surface, safe-drawing/IME padding, a weighted scrolling body and an anchored action. There is no implemented tablet rearrangement or validated large-font layout to record as canon. `RoundAction` has explicit compact geometry (44dp), and calendar day chips are also narrow (44dp); neither establishes compliance with the pinned effective touch-target minimum (48dp). Native hit-area expansion has not been checked here, and the compact measurements are not promoted into touch-target tokens.

**The Safe Space Rule.** Keep content clear of system bars and the floating navigation; retain scrollable content and native IME handling rather than fixing a whole screen to capture dimensions.

Dashboard rings retain the observed small/large/small relationship, equal center alignment and stronger central metric. Source-specific dashboard composition stays in the surface contract. The session uses a compact timer header, horizontal exercise selection and one selected exercise body. This topology is established; its remaining card/notes/rest/footer composition is a documented parity gap, not a template for future source-matching loggers.

## Elevation & Depth

Ordinary shared custom surfaces use tonal layering. The approved floating navigation material is the scoped exception: native `graphicsLayer.shadowElevation` is (6dp) at rest and increases by up to (3dp) with scroll energy; these native elevation values do not specify a CSS shadow blur. Surface tint is transparent. Cards and action wells separate by charcoal levels; outlines separate fields and fine Settings rows. The navigation rim provides a thin structural edge, not an ambient glow. Material dialogs, menus, sheets and ripple/state layers remain native library behavior; their exact elevations are not independently extracted into custom tokens.

**The Tonal Depth Rule.** Separate ordinary custom surfaces with the existing charcoal levels and restrained lines; keep the Nuvio gradient confined to the navigation rim.

Navigation uses a shared selection transition (280ms) and shell response (180ms), both with `FastOutSlowInEasing`; the selected icon scales to (1.04×) over (180ms) with Compose's default tween easing. Root user scrolling accumulates signed energy, clamped to ±1, from consumed vertical distance divided by 160; it drives only the material's vertical shift (at most 2dp), width contraction (at most 0.8%) and depth response. The fixed anchor and touch row do not move. After (140ms) without a new energy/tab effect, energy resets to zero and the shell settles through its response tween. `ValueAnimator.areAnimatorsEnabled()` gates scroll-energy updates; Compose state animations follow Android animation scaling. These are implemented controls, not proof of every accessibility or interruption state. No auto-hide, loop or global motion language is established.

## Shapes

Use the shared rounded surface for reusable cards, the smaller control radius for primary actions and exercise images, and full capsules for navigation and the exercise search field. Settings uses its own group and icon-tile radii. Small muscle/media containers also have a local radius (10dp); that isolated value is not promoted to a general scale step.

Navigation clips to a capsule, with a fine gradient border (1dp); Settings groups have faint borders and inset dividers (0.5dp). Ordinary cards are flat fills rather than universally bordered panels. Native Material field/chip shapes remain library defaults where the source does not override them. Do not copy the navigation silhouette onto every surface.

## Components

### Buttons

White primary actions are direct and readable. `PrimaryAction` fills available width, uses the control radius and a minimum height (52dp), and accepts an optional leading icon (20dp) separated from text by compact space. Its internal content padding and disabled treatment remain Material defaults; there is no invented fixed button padding token.

Outlined and text actions provide subordinate choices. The logger's Add Exercise outlined variant uses the control radius and minimum height (50dp); other outlined buttons retain their native defaults. Text actions retain the theme foreground, with muted color where explicitly set. Native click/ripple, focus and disabled behavior are inherited; no custom hover color or focus-ring value has been verified. The Finish footer's placement is a logger parity difference, not a required primary-action layout.

### Chips

Exercise filters use native `FilterChip` and `InputChip` with visible selection. The selected container follows the white secondary-container theme with black text; shape, padding and state layers remain Material defaults. Gym/type/laterality/filters/favorites are filter contexts, not a new navigation system. The dashboard's two-part scope selector is separate: white selected segment on raised charcoal, persisting the data scope.

### Cards / Containers

Reusable analytics, routine and history containers use surface charcoal, the shared surface radius, and inset/page padding without custom shadows. Metrics remain aligned with their units and supporting labels. The current logger wraps its selected exercise and notes in a similar card, but source evidence shows a different logger treatment; do not generalize that placement as source canon.

### Inputs / Fields

Search uses an outlined native field clipped to a capsule, with a search icon and contextual clear action. Metric/name/notes fields use `OutlinedTextField` defaults unless explicitly overridden. Set weight and reps use numeric keyboards, unit-bearing descriptions, visible/semantic validation and completion guards; RIR is optional. The sidecar's input preview represents their basic outline vocabulary, not source keypad or target-editor equivalence. No custom error border thickness, focus animation or disabled opacity is established.

### Navigation

The Nuvio-derived capsule carries four fixed icon/label destinations and a single moving selected surface beneath them. Shell and selected heights are in frontmatter; selected offset starts at (4dp), and slot width is `(available width - 8dp) / 4`. Selection animates over (280ms) using the easing described above. Icons are (26dp), labels use the label role, selected foreground is white and inactive icon/text tones are distinct. Each target uses `Role.Tab` in a selectable group; the moving surface does not move the targets. The pill overlays primary destinations with transparent space around it and yields to detail/session routes. Only the shell and its selected material receive the scroll transform; selected icons receive their own small scale response. Labels and tab hit areas remain at their fixed anchor.

**The Fixed Targets Rule.** Animate one shared selected capsule beneath fixed tab targets; confine scroll translation, width and depth response to the shell material, retain the fixed anchor and touch row, and preserve native selection semantics, system insets and trailing scroll clearance.

### Grouped Settings

Settings retains its own dark canvas, oversized title, monochrome icon tiles and grouped rows. Rows have horizontal padding (16dp), vertical padding (15dp), and a gap (15dp); icon tiles are (40dp) with icons (25dp). Dividers begin after the icon column (71dp). Existing uppercase category labels organize native settings groups; they do not license decorative eyebrows on other surfaces. The captured default frame supports this exception, not all settings branches or accessibility states.

### Setup

Setup explicitly sets `Surface(color = OF.top, contentColor = Color.White)`, retaining the existing charcoal/white palette. Centered local headings reuse the workout display font; a numbered vertical rail distinguishes current, completed and upcoming stages with white, check marks and muted tones. The anchored action uses `PrimaryAction`, while the body remains scrollable with system/IME insets. These patterns describe this requested setup, not a replacement global layout or typography ramp.

The local flow persists partial input and step, supports deferral and Settings re-entry, and leads to the existing manual-program editor or the dashboard. Missing intermediate source forms make the local profile a reconstruction. Source screenshot/XML timestamps are not synchronized pairs. Source account login, Health Connect and smart-program backends are not implemented; their presence in source references or explanatory local UI does not establish working integration or recovered parity. The surface contract and `docs/open-factor/ONBOARDING.md` own the detailed flow.

### Selected Exercise Logger

The strip presents photographed exercise tiles (80dp by 104dp) with a selected white underline (3dp); only the selected exercise's set editor is shown. Set option/completion controls use a (48dp) column. Completing sets uses a green check control plus a state change, with invalid metrics preventing completion. Pause/resume and minimize preserve training continuity; summaries now share pause-excluding elapsed calculation. Exercise Auto-Next is persisted and can be disabled in Settings. Circular selection for larger exercise lists is a reconstruction policy, not a fully recovered source algorithm.

Not canonized: the bright add tile, exercise notes/card treatment, inline rest bar and fixed Finish footer differ from the private source logger. Exact source target editing/apply-scope behavior, custom keypad, metric-specific layouts, complete set types, rest sheet and exact spacing remain unresolved; local manual-program target support does not close those gaps. The catalog-count discrepancy and account-specific promotion behavior also remain unresolved, rather than becoming design rules. The older logger review's saved-duration defect is corrected in current source; do not perpetuate it as an outstanding finding. Actual TalkBack speech, enlarged type, validation recovery and source summary equivalence are not established by these still captures or code inspection.

The schema-v2 sidecar translates sampled native primitives into self-contained HTML/CSS previews, using one CSS pixel per native dp/sp only for panel illustration. It embeds no reference fonts or photos. Browser hover/focus treatments are preview affordances, not assertions about tested Android rendering; native input/chip library defaults are representative rather than measured web equivalents. Synthetic tonal ramps are panel metadata, not additional application palette tokens.

## Do's and Don'ts

### Do:

- **Do** preserve the pinned Workouts hierarchy and the user's Nuvio navigation/settings exceptions.
- **Do** use the Neutral Foreground Rule for action, training-information and completion colors.
- **Do** distinguish locally imported MacroSans/DM Sans appearance from the public Android sans fallback.
- **Do** retain fixed navigation targets, native selection semantics, system insets and persistent session state.
- **Do** keep source parity and spoken accessibility claims bounded by their actual evidence.

### Don't:

- **Don't** spread the navigation rim gradient or Settings-specific palette across workout content.
- **Don't** promote the logger's remaining add-tile, notes/card, rest-bar or Finish-footer differences into source canon.
- **Don't** treat system-font leakage in unmapped roles as an approved typography choice.
- **Don't** substitute decorative imagery, fabricated video or glyph text for the real media and vector-icon vocabulary.
- **Don't** infer successful TalkBack, large-font, tablet or IME verification from screenshots or semantics alone.
