# Workout application direction contract

Mode: Operate. Platform: Android. Artifact: `openfactor/app/`.

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

The body-data routes reuse `PageHeading`, charcoal surfaces, native outlined metric fields, and the white `PrimaryAction`. A dated Scale Weight form supports kg/lb input and optional manual body fat. Full Body groups the 18 observed circumference fields into Upper Body, Arms and Legs. Progress Photos provides dated Front/Side/Back attachment, private original-byte storage, full-view reopening and complete backup. Editors scroll above an IME-aware Save action; the shared Scaffold accounts for system bars and display cutouts. Body Data history opens from grouped Settings, Dashboard and the pill's Shortcuts actions. The pill's geometry and motion remain unchanged. Source sheet geometry, Weight Trend and the Visual Body Fat reference are not reproduced by these local forms. `docs/open-factor/PARITY.md` records the local record and backup policies separately from source evidence. No new visual system, generated images or bundled private evidence is introduced.
