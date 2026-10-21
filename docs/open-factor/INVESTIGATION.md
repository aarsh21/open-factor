# Investigation ledger

## Identity and evidence

The supplied APK is the base APK of `com.sbs.train`, version 1.4.1 (130). Its SHA-256 is `32df7bbb6939444957d150641e48c2a2788f8fd6c85bc111df8d6047cd044396`. The installed phone's base APK has the same digest. REA 6.7.0 pulled the base plus arm64-v8a, English and xxxhdpi splits.

- Artifact inventory: `ev_ea18edda42bfc849aab356fc7f3b459754c430d22461e2c7b256dc469c8b531d`.
- Split acquisition: `ev_60880ccecda24bf84052b53cef8cbc6add2b4909ece9d1791d584ce765fd4393`.
- Raw evidence and extracted content remain in the parent workspace's `inspection/private/` directory, outside this public repository.

## Observed packaged content

| Category | Files | Evidence boundary |
| --- | ---: | --- |
| Exercise images | 1,832 | WEBP exercise frame images, not a count of exercises |
| Exercise instruction files | 1,500 | Markdown; ID-to-name catalog still under investigation |
| Equipment images | 297 | PNG files named with IDs |
| Muscle diagrams | 44 | Male/female SVG overlays |
| Body avatars | 4 | Front/back SVGs |
| Rive animations | 2 | App presentation animations |

There are no bundled `.mp4` or `.webm` files in the base APK inventory. This does not establish that the running app has no streamed or downloaded videos.

## Tool setup verification

The user approved REA setup for Codex. `setup --client codex --yes` backed up the Codex config, registered the version-pinned MCP server and installed the matching skill. Scoped `doctor --client codex --skill` reports healthy. A direct MCP initialize/tools-list handshake identified REA 6.7.0 and 207 tools. The existing Codex session needs a restart to expose newly registered tools, so investigation continues through the version-pinned CLI.

Optional Ghidra/Hopper/IDA/JADX/Kuna engines are absent. The base APK contains no `libapp.so`; its arm64 split does. REA Dart AOT inspection of the split reports unrecognized snapshot symbols. This is an analysis limitation, not proof that exercise metadata is absent.

## Runtime observations

- Welcome screen includes Log In and Get Started.
- Onboarding includes Basics, Gym & Equipment, Program.
- Basics offers optional Health Connect sync for steps, weight, body metrics and workouts.
- Full authenticated screens are pending completion of onboarding/sign-in by the user.

## Parity status

Every unobserved flow remains unverified. No one-to-one completeness claim is made at this stage. Update this ledger as screens and behavior are captured.
