# Private source feature reference

The source-app reference is stored outside this Git repository under the parent workspace's `inspection/private/feature-tour/`. Do not commit the videos, screenshots, account data, or extracted original media.

The indexed viewing copy contains 14 chapters and runs about 28 minutes. `indexed/index.html` provides chapter buttons; `indexed/INDEX.md` and `indexed/index.json` record captured flows, gaps, durations and offsets. MP4 chapters also work in compatible video players. All frames decoded successfully and four contact sheets sampled at 15-second intervals were reviewed. Identified notification/launcher interruptions and one idle chunk were excluded. This review does not make the private recording suitable for public redistribution.

| Chapter | Captured scope |
| --- | --- |
| Dashboard | Weekly rings, scope, customization, analytics and selectors |
| Workout programs | Calendar, active program, rest/training days, settings and empty history |
| Exercise library | Categories, type/laterality/resistance filters and performed empty state |
| Exercise detail | Playing demonstration, instructions, properties, equipment, history and rest override |
| Workout creation | Scratch/XLSX choices, name/gym and final unsaved draft |
| Smart program | Wizard options, generated draft, set targets and saving scope |
| Manual program | Rest/day conversion, picker, cycles and set-type options |
| Live logger | Two exercises, reps, completion, rest, pause/minimize/discard and training settings |
| Levels | Anatomy rotation, time windows and empty muscle rows |
| Body data | Weight, measurements, visual reference, photo entry and Weight Trend |
| Gym profiles | Equipment, weights, quantity, icon and prefill choices |
| Settings | Units, language entry, integration, customization and behavior/display controls |
| Data and support | Export/visibility, tutorials/about and support/deletion entries |
| Exercise tools | Custom exercise draft and assessment questions |

No completed source workouts were saved. The account's empty history limits populated records, summaries and Levels evidence. No new programs, gyms or custom exercises were saved/activated. Purchases, deletion, health grants, external support links, cloud behavior and every exercise/set/metric variation remain unexecuted. The source camera/speed interactions and all remote videos are not comprehensively verified.

Regenerate from reviewed private inputs with installed ffmpeg/ffprobe:

```sh
python tooling/build_reference_tour.py /private/path/coverage.json \
  --clips /private/path/videos \
  --exclusions /private/path/redactions.json \
  --output /private/path/indexed
```

`coverage.json` defines chapter clip filenames, observed features and gaps. Exclusions map exact filenames to reviewed second ranges. The tool checks inputs, re-encodes consistent chapter videos, embeds MP4 chapters and writes HTML/JSON/Markdown indexes. It does not identify private content automatically. Review generated media before sharing, and keep provenance distinct from the public code's GPL license.
