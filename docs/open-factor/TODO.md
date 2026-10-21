# Open Factor remaining work

Full source parity remains in progress. Completed checkpoints and independent findings are recorded in INVESTIGATION.md and reviews/.

## Deferred exercise-video acquisition

User decision, 21 October 2026: defer server-side exercise-video collection. In a future explicitly started phone-assisted pass, inspect the source app's normal authenticated playback and available media/cache export paths, then acquire the accessible demonstrations and preserve their exercise/camera mapping for offline use. No phone operations or new media acquisition are part of the current implementation pass.

- Reuse the existing static mapping: 1,831 candidate video paths for 619/1,345 exercises. A candidate path is not a working URL.
- Preserve the existing Firebase 401/App Check failure. Use supported authenticated access; do not bypass integrity checks or copy account tokens into the new app.
- Establish which exercises and camera angles actually have demonstrations, rather than assuming all 1,345 do.
- Validate acquired videos by exercise identity, content type, full decode, duration and SHA-256; retain per-file provenance and an explicit missing-media list.
- Keep source media private until its redistribution terms are established separately from the GPL code. The indexed walkthrough and captured demos are reference evidence, not recovered original media.
- Import verified files with the existing exact-filename asset importer and retest actual playback, camera selection, lifecycle pause and offline availability on the emulator.

Current original-file coverage: **0/1,345 verified**. The bounded public/local search found no downloadable original exercise demo files in the artifacts and official pages checked. Do not retry the same failed route or count promotional animations/synthetic player fixtures as demonstrations.

## Active implementation backlog

- Finish manual-program fidelity: icon selection, exact cycle/apply-scope sheets, circular set/RIR controls, descriptive rest controls and source rest pictogram, advanced sets and calendar placement. The local editor/library/activation and program-targeted session foundation is implemented; full source parity remains open.
- Exact smart-generation, deload and progression behavior; recovery is partial.
- Source logger keypad, metric-specific layouts, targets/warm-up, complete set types and rest sheet.
- Body metrics, charts, records, import/export, integrations and localization.
- Authenticated catalog count discrepancy and spoken accessibility checks.


### Onboarding review follow-up

- [ ] Rebuild welcome around a redistributable own-app workout-preview plate and source masthead/action hierarchy; keep the private source capture out of public assets.
- [ ] Restore regular overview subtitle/stage labels and continuous stage-rail rhythm.
- [ ] Give each gym preset an independent minimum 48dp target; verify enlarged-type/IME reachability.
- [ ] Retain unavailable account/sync/smart/XLSX truth and unresolved source intermediate-form parity.
- [ ] Verify actual TalkBack order/selection/error announcements.

See [the independent review](reviews/onboarding-navigation-01.md); welcome disposition remains rebuild independently of the bounded navigation correction.
