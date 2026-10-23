# Native UI adversarial checks

This bounded Android pass found and corrected three UI defects. It does not
certify the parent's later combined application, private media, source parity,
spoken TalkBack output, or physical-device behavior.

The source started at parent local `main` revision `26607756`, not remote
`origin/main`. Only the existing `open-factor-parity-implementation` AVD on
emulator-5584 was used. It cold-started without wiping userdata or loading or
saving snapshots. The device stayed offline. No Health grant, transfer, provider
operation, uninstall, data clear, private-pack change, or private-original
publication occurred.

## Defects and fixes

### Empty exercise search covered its own guidance

On the root Exercises page, focus Search exercises and enter
`NoSuchExerciseUXProbe`. With the keyboard open, the floating field covered the
empty-state guidance at font 1.0 and part of its heading at 1.3. Dismissing the
keyboard revealed the content. The original APK reproduced this under the same
script used for the fixed APK.

The root no-results state temporarily omits the muscle browsing strip and unused
navigation clearance while the keyboard is open. The existing heading, guidance,
and Clear Filters action then fit above search. The shared `EmptyContent`, root
content extent, pill, populated results, and picker layout remain unchanged.
Keyboard dismissal restores the original layout.

At font 1.0 the final action ends at y=1021 and search starts at y=1338. At 1.3
the action ends at y=1137 and search starts at y=1321. Both controls and guidance
are visible in inspected renders. Text and bounds match the original APK for
all six populated, populated-with-keyboard, and dismissed-keyboard control
captures across both fonts. A scrolled Ring Chin-Up result retains its exact
bounds after exercise settings, Info, Close, and Back at 1.3.

### Long exercise notes hid Save and Cancel behind the keyboard

Open Chin-Up's Exercise Note editor and enter 25 repetitions of
`Long exercise note with form cues. `, including the trailing space. The full
875-character note made the original dialog extend behind the keyboard and
status bar at both fonts.

The note field now shows at most six lines and scrolls its remaining text.
No string limit or saved-content truncation was added. The original Save and
Cancel labels ended at y=1776 and y=1801 for normal and enlarged text, below the
keyboard's y=1517 top. They now end at y=1255 and y=1385, above that same keyboard.
Inspected renders show the title and help clear of the status bar.

Native input, Save, restart, and reopening preserve all 875 characters at both
fonts. Editing and Cancel leave the saved application state unchanged.

### Muscle tiles did not expose their selected state

Selecting Chest changed its border and filtered the results, but the owning
clickable accessibility node exposed no selection state. The tile now uses
selectable button semantics and retains the existing second-tap clear behavior.

Direct `AccessibilityNodeInfo` inspection at both fonts confirms
`checked=false/true/false` and `Not selected/Selected/Not selected` across initial,
selected, and cleared states. Compose exports this non-tab selection through
checked state and a state description, not `isSelected`. The label remains the
Chest child of the same clickable owner. Selected muscle-strip crops are
pixel-identical to the original at both fonts, with ImageMagick absolute error 0.
This verifies native metadata, not spoken TalkBack.

## Other checks and limits

- All four roots retain readable navigation labels at normal and enlarged text.
  Central plus opens Shortcuts without selecting another destination. Expanding
  the sheet reveals all seven actions. Back from the expanded sheet first
  collapses it, then dismisses it.
- Actual scrolling positions the complete Health Connect Settings row above
  navigation at both fonts. Its panel footer and the final Settings rows are
  reachable. Intermediate rows behind the floating pill are not clipping defects.
- Empty Dashboard and Workout states, ten populated saved routines, and a long
  final routine title remain readable with final controls above navigation.
- Picker Done and system Back return without changing saved data at both fonts.
  At 1.3, scrolling its short keyboard-visible list reaches Clear Filters while
  Done remains visible. The guidance is partially positioned at the viewport
  edge in both captures. These images do not establish complete guidance
  visibility or a persistent clipping defect.
- Public exercise media fallback is expected. This public APK contains only the
  three factual catalog assets. Filled autoplay media, camera switching, speeds,
  and the no-timeline contract require the parent's private-reference run.
- This pass does not measure performance, certify tablet or landscape layouts,
  or repeat every prior functional acceptance case. The parent's immutable
  combined artifact still needs native acceptance after integration.

## Artifact identity and verification

The unchanged public APK is
`d9e1b8e4845ec6f65a8603fdb8846f095ef712170c67b4202e688bf10c65adaa`.
The search-only candidate is
`24ae228da7be2519a8b07cc848268cf3dbc26638bc03f8e0c9c5a0982294dda3`.
The final worker candidate with all three UI fixes is
`afbbb98fefca7affabb9c7e2b9f21f5a9d04f6364ce5ff06e6d37b5023ebd21c`.
Both guarded builds pass 88 JVM tests with no failures, errors, or skips, and
lint with zero errors and 11 warnings. The APKs use the compatible debug
certificate. Installation used `--no-streaming -r -d`, without re-signing.

Locally excluded evidence is under `.amp/in/artifacts/ux-adversarial/` in the
`open-factor-astra-ux-proof` worktree. Before and after images use
`search-before-final-*`, `combined-ui-after-*`, `note-before-final-*`, and
`note-after-*`. Result JSON, owning-node dumps, window dumps, actual build logs,
`note-ime-clearance.json`, and `muscle-pixel-comparison.txt` accompany them.
Focused repeatable drivers are under `.amp/in/ux-adversarial/`.

Failed driver attempts remain separate. Corrections include distinguishing
`Chin-Up` from the `Chin` substring in Machine, targeting a result rather than
the identically named query field, rejecting null accessibility roots, and
reading Compose's checked-state representation. A rapid long-text injection
delivered only 623 characters at 1.3. The completed before and after runs use
paced input and assert the complete 875-character value. Picker reentry retains
its query and requires an explicit clear. A height-versus-width heuristic missed
the short vertical picker list, so its final clearance check used the actual
scrollable node. These setup failures are not application regressions or an
uninterrupted aggregate pass.

## Exact restoration and release

Both incoming APKs were independently pulled and hashed before replacement.
The full private directory archive and file manifest were saved before mutation.
The restored app hash is
`18202bbcf6f7007030b6ab6f01defe175c36183e433d7e02f828302adeced569`.
The test APK remains
`482a767f3b84677d5674d3fa9e3fb75d142d7336221288371c1bb3019dc1d227`.
The exact 28,869-byte preferences file has hash
`ba977aa4bda91235b549ae8a81635048059f02c915a5d0b085ebcfbc0f8ec79e`.
The zero-byte `profileInstalled` and complete user-file manifest match. The
incoming photo directory was empty, so no nonempty-photo preservation claim is
made.

Font 1.0, automatic rotation 1, user rotation 0, system night no with its secure
key absent, Wi-Fi 0, and mobile data 0 were restored and read back. Own device
dumps, transfer archive, and accessibility probe were removed. The app has no
process and its package reports `stopped=true`. `restoration.json` and
`restoration.log` record the completed checks. Emulator-5584 is explicitly
released to the parent. All changes remain local and unpushed.
