# Manual programs

Manual programs are a local development feature. Smart generation, automatic progression and complete reference-app parity remain unfinished.

"Cycles" is source terminology. Captured manual drafts show **7 Cycles**, and the connected source app displays **Cycle 1**. Seven is the scratch default, not a source restriction. Stored programs distinguish **Cycles** from **Weeks**; manual and legacy plans default to **Cycles**. The source's complete labeling rule remains unresolved, so the editor does not offer an inferred Week/Cycle switch. See [the parity ledger](PARITY.md).

1. Open **Workout → Programs → Create program**. Name the plan, choose its color and icon, and continue.
2. The first day is **Rest**. Add exercises to convert it into a workout day, or use **Add Day** to keep the rest day and create another day.
3. Choose a gym, rename the workout day, and use the horizontal day actions to reorder it. An exercise's overflow menu removes or reorders that exercise.
4. Open **Program Settings** to change the count, name, color, or icon. Settings remain pending until **Save**. Increasing the count copies the final targets into new cycles with new set identities. Reducing the count requires confirmation before removing trailing targets. This resize policy and the 1–52 limit are local behavior, not recovered source behavior.
5. Open an exercise's cycle target card. Set optional minimum and maximum reps, target RIR, skip, and rest overrides. Choose one cycle or the all-cycle view. **More Options** applies the selected prescription to the current cycle, the current and following cycles, or all cycles, for the exercise, day, or program. Recipients without a reps metric do not receive rep or RIR targets. Rest zero disables the timer; an empty rest override falls back to exercise defaults, then the workout timer.
6. **Save to Library** stores the plan. **Activate Program** also selects it as the active plan. Leaving the editor offers to keep or discard the draft; saved plans remain unchanged until saved. These local save and activation actions do not establish source-equivalent placement or advancement.
7. In the library, choose a cycle and start a workout day. Planned targets are available through **Targets** in the logger. Performed values start blank, separate from targets. Enter the exercise's required metrics before completing a set. A current workout is protected from replacement.

Opening targets, changing the viewing scope or saving without edits preserves existing cycle-specific targets. Differentiated targets open on the first cycle rather than showing the first cycle's values as if they applied to all cycles. The all-cycle view warns before edits would replace differing targets.

Programs and drafts are included in local JSON backups. Finishing a program workout retains program, day, cycle and target identity in history. Deleting a library plan preserves workout history.

The Workout calendar expands into consecutive weeks across month boundaries, with history markers and date navigation. The active program and workout library collapse independently. Saved workouts and exercises in a new workout support explicit ordering. No calendar schedule, drop placement, inferred next workout, or automatic program advancement is implemented. The source shows expanded calendar and drag affordances, but retained evidence does not establish drop outcomes or the Daily/Weekly Scroll preference's behavior. The eight-week browsing window, arrow navigation, and Move Up/Down actions are local choices.

The editor supports Standard Sets and public vector icon equivalents. Its Day/Cycle muscle summaries use primary-muscle targets from the first cycle. Exact source icons, Myo/Failure sets, supersets, XLSX, calendar placement, automatic progression, and deload integration remain unfinished. Original exercise videos are imported locally; see [the media receipt](media-handoff/RECEIPT.md).

Native source recovery at `ee7ef4–ee893c` establishes selected, following, and all-cycle filters, plus exercise, day, and program recipient filters. Recovery at `e551d0–e55298` establishes non-rep target sanitation. These findings support the scope controls, not a claim that every save or replacement outcome matches the source.
