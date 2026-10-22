# Local progress photos

The retained `source/07-photos` capture shows a date, Front/Side/Back slots,
Save, dismiss, and delete controls. The body-data tour also shows the photo-entry
destination. Attachment selection, source saving, populated history, and deletion
outcomes were not captured. The local behavior below is an adaptation, not a
claim of recovered source Save semantics.

The editor uses a compact bottom sheet with a centered date and title, labeled
square slots, dashed empty borders, and Save below the slots. Populated slots
show replacement and removal controls. Previews fit the original image without
cropping it. The date header opens a separate date input with calendar selection.

The tour shows Photos over the Levels page around 100 seconds and dismissal
to that same page around 107.5 seconds. Local quick-log photo and measurement
sheets keep their calling screen behind them and return there on dismissal.
History edits keep Body Data behind them and return to that history. Save still
opens local Body Data history; the source Save destination is unobserved.

## Record and storage policy

Body Data → Photos and Shortcuts → Log Photos open the dated photo workflow.
Each record needs at least one of the three views. Same-day records coexist by
stable ID. Reopening permits a date change, replacement, individual-slot removal,
full-image viewing, and confirmed record deletion. Save applies edits. Close,
Back, an outside tap, or a downward sheet drag discards unsaved changes.
Cancelling the Android document picker does not change a slot.

The Android document picker selects existing images. Camera capture, editing or
cropping the image itself, comparison tools, charts, and trend or visual-fat
calculations are not implemented.

Attachments are copied into the app's private `noBackupFilesDir/progress-photos`
directory, named by SHA-256. Records contain hashes, not document URIs or source
paths. Reopening therefore does not need a retained URI permission or the source
file. Original bytes, orientation metadata, and other embedded metadata are
preserved. Nothing is added to the public gallery, and no Internet or storage
permission is requested. Exercise assets are untouched.

Removing a slot takes effect on Save. Cancelling an edit retains its saved
images. Unreferenced imports are cleaned when leaving or opening a photo editor;
replaced/deleted images are removed after the record update succeeds. A shared
image remains while any saved record references it. Process interruption can
leave an unreferenced private import until the next editor cleanup. Saved
records and their files remain durable.

## Complete JSON backup

Export Backup includes photo records and a `photoAssets` object mapping hashes
to Base64 original bytes. Image bytes stream to the chosen document instead of
collecting every attachment in memory. The normal private app-data JSON contains
only references. Backups include original image metadata and can contain
sensitive images, so the UI tells the user to keep the exported file private.

Restore stages and verifies every attachment, hash, image, and record before
showing confirmation. Missing, corrupt, duplicate, unreferenced, or invalid
attachments reject the whole backup without changing saved training or photos.
Confirmation installs verified images before replacing app data; Cancel removes
the staged copies. Restoring an older backup without photos removes current
photo records and their unreferenced private files, after the explicit warning.

Local safety limits are 20 MiB and 100 megapixels per image, 1 GiB of JSON backup
text, and a bounded metadata allocation budget. These are implementation limits,
not source restrictions. Export applies the same metadata budget as restore and
rejects an oversized backup rather than writing one the app cannot reopen.

## Reproduce the checks

Build from `openfactor/` with `:app:testDebugUnitTest`, `:app:lintDebug`,
`:app:assembleDebug`, and `:app:assembleDebugAndroidTest`. Install both APKs on an
explicitly disposable emulator, then run the Android tests and the photo smoke:

```sh
adb -s emulator-5582 shell am instrument -w \
  org.openfactor.app.test/androidx.test.runner.AndroidJUnitRunner
python tooling/android_photo_sheet_smoke.py --serial emulator-5582 \
  --output .amp/in/artifacts/photo-sheet/ui
```

The older `android_progress_photos_smoke.py` assumes an empty photo history and
an inline date. Before use, its date steps need Change photo date, a typed date,
and Apply date. Its final shortcut-dismissal check needs the caller instead of
Body Data.

The photo-sheet smoke preserves existing photos. It checks the compact square
slots, invalid dates, picker Cancel, three attachments, full-image reopen after
source deletion, replacement, removal, and all four dismissal paths. The flow
also checks draft recreation at 1.3× text, complete native export and restore,
rejected invalid backups, confirmation Cancel, deletion, and process restart.
Quick-log photo and Full Body checks cover caller return from Settings, Workout,
Exercises, and Dashboard. Full Body checks use Close and Back, not outside taps.
The script leaves one synthetic photo record and restores prior network and
font settings. Existing training, weights, measurements, and original private
photo bytes remain unchanged. Keep app data, screenshots, and private source
evidence out of Git.

## Remaining source parity

The exact source pose icons are not bundled; the empty slots use a standard
Material person icon. Source attachment selection, populated history, source
save and deletion outcomes, and same-day policy remain unobserved. Local date
dialogs, typography, responsive spacing, privacy copy, and populated-slot actions
are adaptations. Camera, crop, comparison, and trend or visual-fat calculations
are not implemented or inferred.
