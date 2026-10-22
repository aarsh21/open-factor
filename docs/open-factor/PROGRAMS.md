# Manual programs

Manual programs are a local development feature. Smart generation, automatic progression and complete reference-app parity remain unfinished.

"Cycles" is source terminology, not an invented feature: captured manual drafts show **7 Cycles**, and the connected source app displays **Cycle 1**. Seven is the observed scratch default; a fixed seven-cycle editor is our current limitation, not a proven source restriction. Weekly generated plans use **Weeks** and a separate **Deload**. The complete labeling rule and programming algorithms remain unresolved; see [the parity ledger](PARITY.md).

1. Open **Workout → Programs → Create program**. Name the plan, choose its color, and continue.
2. The first day is **Rest**. Add exercises to convert it into a workout day, or use **Add Day** to keep the rest day and create another day.
3. Choose a gym, rename the workout day, and use the horizontal day actions to reorder it. An exercise's overflow menu removes or reorders that exercise.
4. Open its **7 Cycles** target card. Set optional minimum/maximum reps, target RIR, skip and rest overrides. Choose a cycle to edit only that cycle; an intentional edit in the all-cycle scope applies to all seven cycles.
5. **Save to Library** stores the plan. **Activate Program** also selects it as the active plan. Leaving the editor offers to keep or discard the draft; saved plans remain unchanged until saved.
6. In the library, choose a cycle and start a workout day. Planned targets are available through **Targets** in the logger; logged RIR remains separate. A current workout is protected from replacement.

Opening targets, changing the viewing scope or saving without edits preserves existing cycle-specific targets. Differentiated targets open on the first cycle rather than showing the first cycle's values as if they applied to all cycles. The all-cycle view warns before edits would replace differing targets.

Programs and drafts are included in local JSON backups. Finishing a program workout retains program, day, cycle and target identity in history. Deleting a library plan preserves workout history.

The current editor supports seven cycles and Standard Sets. Its Day/Cycle muscle summaries use primary-muscle targets from the first cycle. Variable cycle counts, exact source saving scopes, all icons, Myo/Failure sets, supersets, XLSX, calendar placement and automatic progression remain on the parity backlog. Original exercise videos are now imported locally; see [the media receipt](media-handoff/RECEIPT.md).
