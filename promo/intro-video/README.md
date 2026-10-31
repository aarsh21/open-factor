# Open Factor intro film

The 49-second intro video in [docs/media](../../docs/media/open-factor-intro.mp4), made with
[HyperFrames](https://hyperframes.heygen.com) (HTML compositions rendered to MP4).

- `STORYLINES.md`: the three film ideas; `SPEC.md`: the shot list of the one that was built; `REVIEW.md`: review notes.
- `index.html` mounts three scenes from `compositions/`: the phone and camera (`stage.html`), the titles (`titles.html`)
  and the end card (`endcard.html`).
- `tools/make_demo_backup.py` writes the demo training history used for the recordings. Restore it only on a
  throwaway emulator: Restore Backup replaces the app's data.

## Rendering it again

Recordings, stills, sound effects and renders are not committed (see `.gitignore`): the recordings include exercise
demonstration media with separate ownership, and the sound effects' licence does not allow redistributing them.

1. Record the seven takes into `assets/footage/` (`take1-log.mp4` … `take7-library.mp4`, 1080x2400, 30 fps) as
   described in `SPEC.md`, and save `assets/stills/dashboard.png`.
2. Copy `click-soft`, `key-press`, `pop`, `whoosh`, `whoosh-short` and `impact-bass-1` from HyperFrames' bundled
   sound library (`media-use/audio/assets/sfx/` in its skills) into `assets/sfx/`.
3. `npm run check`, then `npx hyperframes render -q delivery --video-frame-format png -o renders/open-factor-intro.mp4`.
