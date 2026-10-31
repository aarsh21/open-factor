# REVIEW: Open Factor intro (storyline A, "One Set")

Delivered: `renders/open-factor-intro.mp4`, 1920x1080, 30 fps, 49.0 s, with sound effects.

## Creative calls
- Picked storyline A (one set travels through the app in one continuous camera). Not built: B "Every Rep, Shown"
  (leans on the exercise videos, whose rights are separate) and C "Five Colors" (kinetic type on palette fields);
  C's palette floods survive as the "Make it yours" beat.
- The app is dark, so the film's home world is the app's own light background (#F5F5F5) with the phone on it,
  instead of a dark void. Palette taps flood the ground with each theme's real action colour.
- The end card is performed from the real app icon geometry (res/drawable/ic_open_factor.xml): the phone crops and
  shrinks into the icon tile, the O rises, the F is printed.
- All footage is real Open Factor 0.9.0 on an emulator with illustrative demo history (promo/intro-video/tools/
  make_demo_backup.py). Two waits were cut: the guide video's loading spinner and the anatomy map drawing in.

## What was measured vs looked at
- Measured: `hyperframes check` passes with no errors; every rendered frame's brightness was scanned (no blank or
  flash frames); audio onsets measured with `hyperframes audio` and land on the on-screen taps.
- Looked at: contact sheets of the render at every beat and seam.
- Not checked by anyone: how the sound actually sounds. There is no music bed: music needs a HeyGen sign-in
  (`npx hyperframes auth login`), after which a track can be added as one `<audio>` in index.html.

## Weakest shot
- 20.8-24.4 s, Finish to summary: the summary shows "4:17" duration because the demo workout was logged quickly.
  With more time: record a longer real session, or crop the summary below the duration row.

## Warnings kept on purpose
- `nested_media_start_basis_ambiguous`: video start times inside `compositions/stage.html` are intentionally local.
- `timeline_track_too_dense`: the titles overlay keeps all 11 titles in one file so their timing reads in one place.
- `first_frame_empty` / `canvas_covered`: a capture artefact of the checker; the rendered frame 0 shows the app.
  `hyperframes snapshot` also returns occasional blank frames; the render itself has none.
