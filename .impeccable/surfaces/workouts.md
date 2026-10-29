# Workout application direction contract

## Current user direction — October 2026

Retain meaningful features and original exercise media as the app's differentiator. The library opens rich guidance first; advanced exercise settings remain an explicit secondary action. The logger has horizontal one-tap exercise tabs, a photo/title header and a separate Guide action that opens original videos/instructions/anatomy/history above the active set. Switching exercises or dismissing guidance preserves entered values. Anatomy stays readable with muscle selection and original male/female figures. Programs remain accessible from Workout; actual training counts replace the empty active-program home card.

The current brief replaces one-to-one parity as the acceptance target. Mode remains Operate and the existing charcoal/white system remains. Workout is the opening destination. One unit choice completes initial setup; Start workout opens exercise selection directly. Every exercise picker, including template creation, ranks the complete catalog, including aliases, abbreviations, combined terms and bounded typos. All equipment is the default. Advanced filters expand on request and IME padding belongs to the picker viewport. Plus opens shortcuts with Create template instead of New Program; Log Photos first shows Front/Side/Back slots, then the chosen slot opens its camera, and Log Weight is a native bottom sheet that preserves the caller and active workout. Active logging exposes Finish, Add set and Warmup with weight/reps fields and native feedback. Titles have no explanatory captions. One rest countdown stays visible without hiding set inputs. Reopening an active workout restores the session; notifications open it directly.

Dashboard restores the three Muscles/Sets/Exercises circles with filled medallions, metric icons, stronger numerals and a quiet blue center, using real recorded counts and uses completed working sets, weekly training bars and front/back muscle workload. Direct sets and half-weighted secondary sets have an explicit count policy and exercise drilldowns. Do not imply a strength score, physiological recovery estimate or an arbitrary workload target. Weight trend uses full-width dated readings, readable axes and a labeled trailing seven-day mean of available readings. Missing days are not zero. History uses a week calendar and dated timeline with real totals and muscle maps. Steps offers interactive 7/30-day provider charts. Health Connect refreshes on every foreground visit after initial connection. Date-dependent views refresh on foreground resume and local-day changes. Health Connect has one Connect/Sync action that continues from native consent into real provider reads; the UI distinguishes pending, denied, unavailable and imported data without fabricated success.

Typography uses bundled public DM Sans. Original photos, instructions, anatomy and equipment art are included by static/full modes. Source mode excludes recovered content and retains the licensed preview/icon fallback. Not every exercise has original guidance. Existing programs, body data, history and settings remain functional. See [media packaging](../../docs/open-factor/MEDIA-PACKAGING.md) for content boundaries.

Media fills the available width at its natural portrait aspect, with side-mounted camera thumbnails and no added black side margins. The full frame scrolls naturally on short screens; the compact speed/transport capsule floats over the top of the video, clear of the side camera rail. Retry remains immediately above the picture. Explicit Play starts one playlist that cycles available angles and repeats; direct camera selection remains available. Opening a guide performs no video download. Video uses HTTPS when not bundled and a bounded 128 MiB cache. The published [0.3.0 verification](../../docs/open-factor/releases/v0.3.0.md) remains historical; [0.5.0 evidence](../../docs/open-factor/releases/v0.5.0.md) records current changes and verification.

Mode: Operate. Platform: Android. Artifact: `openfactor/app/`.

## Historical parity contract

The sections below record the earlier brief and investigations. The current direction above supersedes their parity gates, original-media exclusions and route descriptions.

## THESIS

Reproduce the observed MacroFactor Workouts 1.4.1 tasks and visual hierarchy in Open Factor, with the user's explicit Nuvio floating-pill navigation and grouped-settings exceptions. This is a fidelity task, not a new visual concept. Full parity remains open in the investigation ledger.

## OWN-WORLD

The incumbent references define the world: wide geometric uppercase workout headings, DM Sans text, neutral charcoal fields, white foreground, aligned weekly rings, blue muscle diagrams and photographic exercise media. Nuvio defines the dark floating capsule, animated selected pill, large Settings title, monochrome icon tiles and grouped rows with inset separators. Preserve these references rather than introducing another aesthetic.

## STORY

Dashboard exposes logged training and the active workout. Workout opens the schedule/library and starts or resumes a session. Exercise discovery leads to instructions/properties and logging. Finishing saves history, which supports review and repetition. Settings controls training preferences and local data. Gym, program and Levels flows are being added against their captured source screens.

## FIRST VIEWPORT

In an idle state, show the source's dashboard heading and weekly small/large/small ring hierarchy, then analytics. In an active session, expose a clear resume control immediately. The Active Program/All Workouts segment selects the dashboard's data scope; it is not navigation disguised as selection. Source customization belongs on the header's sliders action. Nuvio-style navigation remains visible on primary destinations and respects safe areas and font scaling.

## FORM

Native Android Jetpack Compose. Build directly in code. Compact-phone captures must come from Android hardware/emulators. Preserve system Back, IME/inset handling, labeled 48dp touch targets, persistent workout state and timer deadlines. The signature interaction is the Nuvio-derived shared capsule selection beneath fixed icon/label targets. No decorative imagery or fabricated videos replace missing reference content. The public code builds independently of the local reference pack.

## Seed and provenance

Status: authenticated source evidence established; implementation fidelity and complete behavior remain under review. This is an inherited visual system; no concept tournament, generated decision comp, approved comp or QUALITY BAR card was requested or created.

- User-supplied Nuvio navigation/settings screenshots: parent thread attachments ending `b4851237-c17b-429b-a961-07286f8f491b.png` and `589ff689-e6ce-493d-85cb-eb39963ed8f7.png`.
- Nuvio code reference: upstream `c78756d0cbda3337cfff36a95d8b6708f48c0d72`, `core/ui/NavigationBar.kt`, `Tokens.kt`, and `ThemeColors.kt`.
- Workouts APK SHA-256 `32df7bbb6939444957d150641e48c2a2788f8fd6c85bc111df8d6047cd044396`; authenticated native captures reside privately in the parent workspace's `inspection/private/`.
- Current logger checkpoint captures: `.impeccable/review/paku-api26-logger-*.png`; generated Open Factor test workouts on emulator-5554, Android 8/API 26, 1080×1920. Older `.impeccable/review/phone-*.png` are Android 15 emulator captures, 1080×2400, density 420. Neither set is a connected-phone screenshot.
- REA evidence and exact import coverage: `docs/open-factor/INVESTIGATION.md` and `ASSETS.md`. Imported files retain their original ownership and remain ignored by Git.

## Verification and remaining boundaries

Use unit/Android storage tests and ADB smoke flows to validate training continuity. Review enlarged type, IME and semantic labels in the next native pass. The native HTML/CSS detector does not apply. `docs/open-factor/reviews/` records independent dispositions at their actual scope; the foundation review is `fix`, not complete clone acceptance. Do not close the full goal while the parity ledger has required work outstanding.

## Body-data extension

The body-data routes reuse `PageHeading`, charcoal surfaces and the white `PrimaryAction`. A dated Scale Weight form supports kg/lb input and optional manual scale fat. Full Body uses the observed dated sheet/header and filled labeled fields, with manual Visual Body Fat before the 18 circumference fields grouped into Upper Body, Arms and Legs. Its read-only Reference sheet discloses absent art in public builds; private originals require an explicit local build input. Progress Photos uses a compact dated sheet with square dashed Front/Side/Back slots, private original-byte storage, uncropped full-view reopening and complete backup. Quick photo and measurement sheets keep their real caller underneath and return there on dismissal; history edits return to Body Data. Editors scroll above an IME-aware Save action; the shared Scaffold accounts for system bars and display cutouts. Body Data history opens from grouped Settings, Dashboard and the pill's Shortcuts actions. Raw charts keep scale fat and visual fat separate; range chips and history tabs accommodate enlarged type. Selection identifies a saved entry without introducing smoothing or inferred trend values. The pill's geometry and motion remain unchanged. Exact source spacing, pose art, populated history, Weight Trend and visual-fat initialization/estimation remain unaccepted. Full Body's outside-tap area can be covered by the status/cutout region; Close and Back remain available. `docs/open-factor/PARITY.md` records the local record, range and backup policies separately from source evidence. No new visual system or generated substitute imagery is introduced, and recovered originals remain private.

## Exercise creation and discovery extension

Custom creation follows the five observed steps: basics and explicit metrics, muscles, equipment, final details, and review before Create. Native sheets keep Cancel/Done reachable above the IME. Edit Duplicate prefills an unsaved copy; recovered Region facts fill known catalog types without guessing missing values. Alias and instruction editors have bounded visible lines without discarding long text. Blank performed values remain distinct from prescribed targets. Library queries and filter changes reveal the first result, while an unchanged-query detail return retains the reading position. The Resistance sheet uses equipment entities, grouped rows and a bottom name filter, preserving the inherited palette and source-observed hierarchy. Root selection opens exercise settings, then a closeable Info sheet without Add; contextual picker detail retains Add. Pinned exercise notes display separately from workout-entry notes and never populate new history entries. Existing saved notes remain unchanged. Rest defaults copy into fresh entries without rewriting saved drafts, sessions or history. All, Performed and Deprioritized have documented local membership rules. Single-entity selection, metric-family validation and muscle cycling are also local policies, not recovered source outcomes. Modified/Archived, source ranking, remaining lifecycle actions and the custom keypad remain separate parity work.

Info combines recognized Type and Region facts into the existing six editor labels without a redundant Region row. Unresolved classifications retain raw facts rather than guessing a category. Detail titles use compact mixed-case typography even when they close with X instead of Back; root headings keep the display face. Shared headings allow three lines with explicit ellipsis for extreme names, preserving actions; this overflow limit is a local layout policy, not recovered source behavior.

## 0.5.2 refinement

All exercise target muscles appear together on the original front/back figures, with a noninteractive Primary/Secondary legend. Exercise history uses full-width recorded-set tables and opens saved workout details. Edit workout uses a separate durable draft; Save validates actuals and checks the original record for conflicts, while discard preserves history and active workouts. Log all sets is atomic for valid pending logs. Effort is optional, prompted once after all exercise logs complete, and applies only to the last working log; rating/skip survives recreation. Dashboard has calendar Week/Month totals and matching workload with no redundant bar chart.
