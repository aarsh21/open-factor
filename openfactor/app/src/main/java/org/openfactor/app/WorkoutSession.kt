package org.openfactor.app

import androidx.compose.foundation.*
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSession(
    data: AppData,
    exercises: List<Exercise>,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onExercise: (String) -> Unit,
    onFinished: (String) -> Unit,
    onWorkoutSettings: () -> Unit,
    onGymSettings: () -> Unit,
    onOpenWorkout: ((String) -> Unit)? = null,
    onSwapSearch: ((String) -> Unit)? = null,
    swapPick: Pair<String, String>? = null,
    onSwapPickHandled: () -> Unit = {},
    onExerciseSwapped: (ExerciseSwapUndo, String) -> Unit = { _, _ -> },
    onSetRemoved: (SetRemovalUndo, String) -> Unit = { _, _ -> },
    /** An Undo snackbar is showing; effort sheets wait so they never end it. */
    undoOffered: Boolean = false,
    /** Live keypad height, so snackbars sit above the keys instead of over Log set. */
    onKeypadInset: (Dp) -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val motion = LocalAppMotionEnabled.current
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }
    var timerSettingsError by remember { mutableStateOf(false) }
    val workout = data.active
    if (workout == null) {
        Column {
            PageHeading("Workout", onBack)
            EmptyContent(
                Icons.Rounded.FitnessCenter,
                "No active workout",
                "Start a workout from the Workout tab.",
                "Back",
                onBack,
            )
        }
        return
    }
    var guidanceExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var workoutNotes by remember { mutableStateOf(false) }
    var restSettings by remember { mutableStateOf(false) }
    var rateExerciseId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    // Finish leaves the current exercise too: ask what is owed first, then continue to the confirmation.
    var finishAfterEffort by rememberSaveable(workout.id) { mutableStateOf(false) }
    val requestFinish = { if (vm.askEffortBeforeFinish()) finishAfterEffort = true else confirm = "finish" }
    var swapEntryId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    val selected =
        workout.exercises.find { it.id == workout.selectedExerciseId }
            ?: workout.exercises.firstOrNull()
    val restUntil = workout.restUntil
    // Ticks recompose only the clock texts (and this scope once, when rest ends), never set rows.
    val clock = rememberSessionClock(workout.id, restUntil)
    var saveTemplate by rememberSaveable { mutableStateOf(false) }
    val resting by remember(clock, restUntil) { derivedStateOf { restUntil > clock.longValue } }
    TraceRecomposition("WorkoutSession")
    val seenExercises = remember(workout.id) { workout.exercises.mapTo(HashSet()) { it.id } }
    val keypadState = remember { WorkoutKeypad() }
    val keypad = keypadState.takeIf { data.preferences.workoutKeypad }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(OF.top).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onBack) { Icon(Icons.Rounded.KeyboardArrowDown, "Minimize workout") }
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val finishPress = remember { MutableInteractionSource() }
            Button(
                { focus.clearFocus(); requestFinish() },
                Modifier.pressFeedback(finishPress, enabled = workout.completedSets() > 0),
                enabled = workout.completedSets() > 0,
                contentPadding = PaddingValues(horizontal = 16.dp),
                interactionSource = finishPress,
            ) { Text("Finish") }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Workout options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Workout settings") },
                        onClick = { menu = false; onWorkoutSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text("Gym settings") },
                        onClick = { menu = false; onGymSettings() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (workout.pausedAt == null) "Pause Workout" else "Resume Workout"
                            )
                        },
                        onClick = {
                            menu = false
                            vm.pauseWorkout(workout.pausedAt == null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Minimize Workout") },
                        onClick = {
                            menu = false
                            onBack()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Finish Workout") },
                        enabled = workout.completedSets() > 0,
                        onClick = {
                            menu = false
                            focus.clearFocus()
                            requestFinish()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Workout") },
                        onClick = {
                            menu = false
                            rename = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Workout notes") },
                        onClick = { menu = false; workoutNotes = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Save as Workout Template") },
                        onClick = {
                            menu = false
                            vm.saveRoutine(workout.name, workout.exercises.map { it.exerciseId })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Discard Workout") },
                        onClick = {
                            menu = false
                            confirm = "discard"
                        },
                    )
                }
            }
        }
        // Rest lives in this fixed-height row: completing a set never pushes the sets under the thumb.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (data.preferences.showWorkoutTimer || workout.pausedAt != null) {
                SessionElapsedText(workout, clock)
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                resting,
                transitionSpec = {
                    (if (motion) fadeIn(tween(AppMotion.Quick, easing = AppMotion.EaseOut)) else EnterTransition.None)
                        .togetherWith(if (motion) fadeOut(tween(AppMotion.Quick - 30)) else ExitTransition.None)
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                contentAlignment = Alignment.CenterEnd,
                label = "rest controls",
            ) { isResting ->
                if (isResting) Row(verticalAlignment = Alignment.CenterVertically) {
                    val settings = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { restSettings = true },
                        modifier = Modifier.heightIn(min = 40.dp).pressFeedback(settings).semantics { contentDescription = "Rest timer settings" },
                        shape = RoundedCornerShape(50),
                        color = OF.elevated,
                        interactionSource = settings,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            RestCountdownText(restUntil, clock)
                        }
                    }
                    val extend = remember { MutableInteractionSource() }
                    TextButton({ vm.extendRest(30) }, Modifier.pressFeedback(extend), interactionSource = extend) { Text("+30s") }
                    val skip = remember { MutableInteractionSource() }
                    TextButton({ vm.clearRest() }, Modifier.pressFeedback(skip), interactionSource = skip) { Text("Skip") }
                } else TextButton({ restSettings = true }) {
                    Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rest timer")
                }
            }
        }
        WorkoutExerciseSelector(
            workout, exercises, selected,
            onSelect = { focus.clearFocus(); vm.selectExercise(it) },
            onAdd = { focus.clearFocus(); onAdd() },
        )
        LazyColumn(
            Modifier.weight(1f).keypadOutsideTap(keypad) { focus.clearFocus() },
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (workout.exercises.isEmpty())
                item {
                    EmptyContent(
                        Icons.Rounded.FitnessCenter,
                        "Build your workout",
                        "Add an exercise to start logging your sets.",
                        "Add Exercise",
                        onAdd,
                    )
                }
            items(listOfNotNull(selected), key = { it.id }) { se ->
                val knownSets = remember(se.id) { se.sets.map { it.id }.toSet() }
                val e =
                    exercises.find { it.id == se.exerciseId }
                        ?: Exercise(se.exerciseId, "Exercise", "Other", "Other")
                val metrics = remember(e) { e.loggingMetrics() }
                var remove by remember { mutableStateOf(false) }
                var exerciseMenu by remember { mutableStateOf(false) }
                // Saveable, so a half-written note's dialog comes back after rotation or process death.
                var exerciseNotes by rememberSaveable(se.id) { mutableStateOf(false) }
                var templateNote by rememberSaveable(se.id) { mutableStateOf(false) }
                val sourceRoutine = data.routines.find { it.id == workout.routineId }?.takeIf { se.exerciseId in it.exerciseIds }
                val lastNote = remember(data.history, se.exerciseId, workout.id) { lastExerciseNote(data.history, se.exerciseId, workout.id) }
                var superset by remember { mutableStateOf(false) }
                var mergeSides by remember { mutableStateOf(false) }
                val logicalSets = se.logicalSets()
                SideEffect { keypad?.order(logicalSets, metrics) }
                var targets by remember { mutableStateOf(false) }
                val showTargets = se.sets.any { it.targetRepsMin != null || it.targetRepsMax != null || it.targetRir != null }
                // Switching exercises happens between sets (and automatically in supersets): stay instant.
                // Only an exercise new to this workout (added or swapped in) fades into place.
                val fresh = se.id !in seenExercises
                SideEffect { seenExercises += se.id }
                Surface(color = OF.background, modifier = Modifier.animateItem(
                    fadeInSpec = if (motion && fresh) tween(AppMotion.State, easing = AppMotion.EaseOut) else null,
                    placementSpec = null, fadeOutSpec = null,
                )) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExerciseImage(e, Modifier.size(42.dp, 54.dp))
                            Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            TextButton({ focus.clearFocus(); guidanceExerciseId = e.id }) {
                                Text("Guide")
                            }
                            Box {
                                IconButton({ exerciseMenu = true }) { Icon(Icons.Rounded.MoreVert, "Exercise options for ${e.title}") }
                                DropdownMenu(exerciseMenu, { exerciseMenu = false }) {
                                    DropdownMenuItem(text = { Text("Exercise guide") }, onClick = { exerciseMenu = false; focus.clearFocus(); guidanceExerciseId = e.id })
                                    DropdownMenuItem(text = { Text("Swap exercise…") }, onClick = { exerciseMenu = false; focus.clearFocus(); swapEntryId = se.id })
                                    DropdownMenuItem(text = { Text("Targets & rest") }, onClick = { exerciseMenu = false; targets = true })
                                    if (metrics.any { it.isReps }) DropdownMenuItem(
                                        text = { Text(if (data.preferences.rateEffortAfterExercise) "Turn off effort prompt" else "Ask effort after exercise") },
                                        onClick = { exerciseMenu = false; vm.settings { it.copy(rateEffortAfterExercise = !it.rateEffortAfterExercise) } },
                                    )
                                    DropdownMenuItem(text = { Text("Exercise notes") }, onClick = { exerciseMenu = false; exerciseNotes = true })
                                    if (sourceRoutine != null) DropdownMenuItem(text = { Text("Template note") }, onClick = { exerciseMenu = false; templateNote = true })
                                    DropdownMenuItem(text = { Text(if (se.sets.any { it.side != null }) "Use left logs only…" else "Log left and right separately") }, onClick = {
                                        exerciseMenu = false
                                        if (se.sets.any { it.side != null }) mergeSides = true else vm.splitSides(se.id)
                                    }, enabled = se.sets.isNotEmpty())
                                    DropdownMenuItem(text = { Text("Create superset…") }, enabled = workout.exercises.size > 1, onClick = { exerciseMenu = false; superset = true })
                                    if (se.supersetId != null) DropdownMenuItem(text = { Text("Leave superset") }, onClick = { exerciseMenu = false; vm.leaveSuperset(se.id) })
                                    DropdownMenuItem(text = { Text("Move earlier") }, enabled = workout.exercises.first().id != se.id, onClick = { exerciseMenu = false; vm.moveSessionExercise(se.id, -1) })
                                    DropdownMenuItem(text = { Text("Move later") }, enabled = workout.exercises.last().id != se.id, onClick = { exerciseMenu = false; vm.moveSessionExercise(se.id, 1) })
                                    DropdownMenuItem(text = { Text("Remove Exercise") }, onClick = { exerciseMenu = false; remove = true })
                                }
                            }
                        }
                        if (se.supersetId != null) Text(
                            "Superset · " + workout.exercises.filter { it.supersetId == se.supersetId }
                                .joinToString(" + ") { entry -> exercises.find { it.id == entry.exerciseId }?.title ?: "Exercise" },
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = OF.muted,
                        )
                        ExerciseNotes(
                            se, pinned = data.exerciseDefaults[e.id]?.note.orEmpty(), templateName = sourceRoutine?.name,
                            templateNote = sourceRoutine?.prescriptions?.find { it.exerciseId == se.exerciseId }?.note.orEmpty(),
                            last = lastNote, onEditSession = { exerciseNotes = true }, onEditTemplate = { templateNote = true },
                            onDismissLast = { vm.dismissLastNote(se.id) },
                        )
                        if (targets)
                            WorkoutTargetsDialog(se, workout.targetRirLimit, { targets = false }) { values, rest ->
                                vm.saveExerciseTargets(se.id, values, rest)
                                targets = false
                            }
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Set", Modifier.width(40.dp), color = OF.muted, fontSize = 12.sp)
                            if (showTargets) Text("Target", Modifier.width(48.dp), color = OF.muted, fontSize = 12.sp)
                            metrics.forEach { metric ->
                                Text(metric.label(data.preferences.pounds, data.preferences.distanceImperial), Modifier.weight(if (metric.isLoad) 1.3f else 1f), color = OF.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.width(48.dp))
                        }
                        Column(Modifier.settleSize()) {
                        logicalSets.forEachIndexed { index, group ->
                            group.components.forEachIndexed { componentIndex, logs ->
                                if (group.root.kind != SetKind.STANDARD || logs.size > 1) Text(
                                    if (componentIndex == 0) "${if (group.root.warmup) "Warm-up" else "Set ${logicalSets.take(index + 1).count { !it.root.warmup }}"} · ${group.root.kind.label}"
                                    else "Myo component $componentIndex", Modifier.padding(top = 10.dp), color = OF.muted,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                logs.forEach { s -> key(s.id) { Box(Modifier.arrival(s.id !in knownSets)) {
                                    // Callbacks capture ids, not the per-edit workout/exercise copies, so
                                    // editing one row leaves the other rows' parameters equal and skippable.
                                    val seId = se.id
                                    val workoutId = workout.id
                                    val setId = s.id
                                    val displayNumber = logicalSets.take(index + 1).count { it.root.warmup == group.root.warmup }
                                    val removedLabel = when {
                                        componentIndex > 0 -> "Myo component $componentIndex"
                                        group.root.warmup -> "Warm-up $displayNumber"
                                        else -> "Set $displayNumber"
                                    }
                                    SetRow(
                                        index, s, data.preferences, metrics,
                                        exerciseName = e.title + (s.side?.let { " ${it.label.lowercase()}" } ?: "") +
                                            (if (componentIndex > 0) " Myo component $componentIndex" else ""),
                                        onChange = { changed -> vm.editSet(seId, setId) { changed } },
                                        onComplete = {
                                            if (keypad?.logging != true) focus.clearFocus()
                                            val before = vm.data.value.active?.takeIf { it.id == workoutId }
                                                ?.exercises?.find { it.id == seId }?.sets?.find { it.id == setId }
                                            vm.toggleSetCompletion(seId, setId)
                                            val after = vm.data.value.active?.takeIf { it.id == workoutId }
                                                ?.exercises?.find { it.id == seId }?.sets?.find { it.id == setId }
                                            // Acknowledge a committed log, never undo, validation or restoration.
                                            if (before?.completed == false && after?.completed == true)
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        onRemove = { vm.removeSet(seId, setId) },
                                        onDelete = { vm.removeSetWithUndo(seId, setId)?.let { onSetRemoved(it, removedLabel) } },
                                        onKind = { kind, isWarmup -> vm.setKind(seId, setId, kind, isWarmup) },
                                        hasExtras = group.components.size > 1,
                                        showTargets = showTargets,
                                        displayNumber = displayNumber,
                                        keypad = keypad,
                                    )
                                } } }
                            }
                            if (group.root.kind == SetKind.MYO && !group.root.warmup) TextButton({ vm.addMyoComponent(se.id, group.root.id) }) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Text("Add Myo component", Modifier.padding(start = 6.dp))
                            }
                        }
                        }
                        if (se.sets.any { !it.completed }) {
                            val canLogAll = se.canLogAllSets(metrics)
                            PrimaryAction(
                                "Log all sets",
                                {
                                    focus.clearFocus()
                                    val before = vm.data.value.active?.takeIf { it.id == workout.id }
                                        ?.exercises?.find { it.id == se.id }
                                    val pending = before?.sets?.filterNot { it.completed }?.map { it.id }.orEmpty()
                                    vm.logAllSets(se.id)
                                    val after = vm.data.value.active?.takeIf { it.id == workout.id }
                                        ?.exercises?.find { it.id == se.id }
                                    if (pending.isNotEmpty() && after != null && pending.all { id -> after.sets.any { it.id == id && it.completed } })
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                Modifier.padding(top = 16.dp),
                                enabled = canLogAll,
                                icon = Icons.Rounded.DoneAll,
                            )
                            if (!canLogAll) Text(
                                "Enter valid values for every unfinished set.",
                                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = OF.muted,
                            )
                        } else se.lastEffortSet(metrics)?.let { last ->
                            TextButton(
                                { focus.clearFocus(); rateExerciseId = se.id },
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                            ) {
                                Text(last.rir?.let { "Last set · $it RIR" } ?: "Rate last set")
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                            }
                        }
                        // Moving on is the most common navigation between sets; it goes through selectExercise.
                        val done = se.workoutExerciseProgress().done
                        val doneOnArrival = remember(se.id) { done }
                        if (done) workout.upNextExercise(se.id)?.let { next ->
                            NextExerciseAction(
                                next, exercises.find { it.id == next.exerciseId }, fresh = !doneOnArrival,
                                onClick = { focus.clearFocus(); vm.selectExercise(next.id) },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val warmupPress = remember { MutableInteractionSource() }
                            val addPress = remember { MutableInteractionSource() }
                            OutlinedButton({ focus.clearFocus(); vm.addWarmupSet(se.id) },
                                Modifier.weight(1f).heightIn(min = 48.dp).pressFeedback(warmupPress), interactionSource = warmupPress) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Warmup")
                            }
                            FilledTonalButton({ focus.clearFocus(); vm.addSet(se.id) },
                                Modifier.weight(1f).heightIn(min = 48.dp).pressFeedback(addPress), interactionSource = addPress) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add set")
                            }
                        }
                    }
                }
                // Starts empty: last time's note is a reference, not today's words to re-save.
                if (exerciseNotes) NoteDialog("Exercise notes", se.notes, { exerciseNotes = false }, {
                    vm.exerciseNotes(se.id, it)
                    exerciseNotes = false
                    null
                }, placeholder = lastNote?.takeIf { se.notes.isBlank() }?.text,
                    supporting = lastNote?.takeIf { se.notes.isBlank() }?.let { "Starts empty. Last time's note stays with that workout." })
                if (templateNote && sourceRoutine != null) NoteDialog("Template note",
                    sourceRoutine.prescriptions.find { it.exerciseId == se.exerciseId }?.note.orEmpty(), { templateNote = false }, {
                        vm.saveTemplateNote(sourceRoutine.id, se.exerciseId, it).also { error -> if (error == null) templateNote = false }
                    }, supporting = "Saved to ${sourceRoutine.name}; shows each time you start it.", limit = TEMPLATE_NOTE_LIMIT)
                if (superset) SupersetDialog(workout, se.id, exercises, { superset = false }) { ids ->
                    vm.groupSuperset(ids)
                    superset = false
                }
                if (mergeSides) ConfirmDialog("Use left logs only?", "Right-side values and completion will be removed. Left-side values stay unchanged.", "Keep left logs", { mergeSides = false }) {
                    vm.keepLeftSide(se.id)
                    mergeSides = false
                }
                if (remove) {
                    ConfirmDialog(
                        "Remove ${e.name}?",
                        "Its sets will be removed from this workout.",
                        "Remove",
                        { remove = false },
                        {
                            vm.removeExercise(se.id)
                            remove = false
                        },
                    )
                }
            }
        }
        keypad?.let {
            val density = LocalDensity.current
            DisposableEffect(Unit) { onDispose { onKeypadInset(0.dp) } }
            Box(Modifier.onSizeChanged { size -> onKeypadInset(with(density) { size.height.toDp() }) }) {
                WorkoutKeypadPanel(it) { focus.clearFocus() }
            }
        }
    }
    // First match wins, as with the linear find it replaces.
    val catalogById = remember(exercises) { HashMap<String, Exercise>().apply { exercises.forEach { putIfAbsent(it.id, it) } } }
    fun owesEffort(entry: SessionExercise) = entry.pendingEffortSet(
        loggingMetricsOf(catalogById[entry.exerciseId]),
    ) != null
    val promptedExercise = when {
        !data.preferences.rateEffortAfterExercise -> null
        // A modal sheet over a live Undo offer would end it (reviewing counts as an edit): wait it out.
        undoOffered && !finishAfterEffort -> null
        data.preferences.effortPromptTiming == EffortPromptTiming.LAST_SET -> workout.exercises.firstOrNull(::owesEffort)
        // Owed for an exercise already left (recorded by the view model). Never stacked on another
        // session sheet or dialog; an automatic advance also waits out the running rest timer.
        guidanceExerciseId != null || swapEntryId != null || restSettings || confirm.isNotEmpty() || rename || workoutNotes -> null
        else -> workout.effortPrompts.firstNotNullOfOrNull { prompt ->
            workout.exercises.find { it.id == prompt.exerciseId }
                // After rest, but never by yanking the keypad away from someone already typing the next set.
                ?.takeIf { (!prompt.afterRest || (!resting && keypad?.open != true)) && it.exerciseId in catalogById && owesEffort(it) }
        }
    }
    if (finishAfterEffort && promptedExercise == null) LaunchedEffect(Unit) { finishAfterEffort = false; confirm = "finish" }
    val ratedExercise = rateExerciseId?.let { id -> workout.exercises.find { it.id == id } } ?: promptedExercise
    // The question takes precedence over the keypad: close it first so the two never stack.
    val keypadOpen = ratedExercise != null && keypad?.open == true
    if (keypadOpen) LaunchedEffect(ratedExercise?.id) { focus.clearFocus() }
    ratedExercise?.takeIf { !keypadOpen }?.let { entry ->
        val exercise = catalogById[entry.exerciseId]
        val set = entry.lastEffortSet(loggingMetricsOf(exercise))
        if (exercise != null && set != null) ExerciseEffortSheet(
            exercise, set,
            onDismiss = { vm.reviewExerciseEffort(entry.id, set.id, set.rir); rateExerciseId = null },
            onSave = { vm.reviewExerciseEffort(entry.id, set.id, it); rateExerciseId = null },
        )
    }
    ExerciseSwapHost(
        data, workout, exercises, vm,
        sheetEntryId = swapEntryId, onCloseSheet = { swapEntryId = null },
        searchPick = swapPick, onSearchPickHandled = onSwapPickHandled, onSearchAll = onSwapSearch,
        onSwapped = onExerciseSwapped,
    )
    guidanceExerciseId?.let { id ->
        exercises.firstOrNull { it.id == id }?.let { exercise ->
            ModalBottomSheet(
                onDismissRequest = { guidanceExerciseId = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = OF.background,
                dragHandle = null,
            ) {
                Box(Modifier.fillMaxHeight(.95f)) {
                    ExerciseDetail(
                        exercise, data, vm,
                        onBack = { guidanceExerciseId = null },
                        onAdd = null,
                        closeable = true,
                        onOpenWorkout = onOpenWorkout,
                    )
                }
            }
        }
    }
    if (restSettings)
        AlertDialog(
            onDismissRequest = { restSettings = false },
            title = { Text("Rest Timers") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var seconds by remember { mutableStateOf(data.preferences.restSeconds.toString()) }
                    val duration = seconds.toIntOrNull()
                    val durationError = seconds.isNotEmpty() && (duration == null || duration !in 0..1800)
                    OutlinedTextField(
                        seconds,
                        { value ->
                            seconds = value
                            value.toIntOrNull()?.takeIf { it in 0..1800 }?.let { valid -> vm.settings { it.copy(restSeconds = valid) } }
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("Rest duration (seconds)") },
                        isError = durationError,
                        supportingText = { Text(if (durationError) "Use 0–1800 seconds" else "Applies to your next completed set") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    )
                    if (!WorkoutNotifications.preciseTimersAllowed(context)) {
                        Text("Android may delay background alerts without precise timer access.", style = MaterialTheme.typography.bodyMedium, color = OF.muted)
                        TextButton({
                            timerSettingsError = runCatching { context.startActivity(WorkoutNotifications.preciseTimerSettings(context)) }.isFailure
                        }) { Text("Precise timer alerts") }
                        if (timerSettingsError) Text("Open Android Settings → Apps → Open Factor → Alarms & reminders.", color = MaterialTheme.colorScheme.error)
                    }
                    listOf(
                        Triple("Use Rest Timers", data.preferences.autoRest, { checked: Boolean -> vm.settings { it.copy(autoRest = checked) } }),
                        Triple("Rest After Last Warm-up Set", data.preferences.restAfterLastWarmupSet, { checked: Boolean -> vm.settings { it.copy(restAfterLastWarmupSet = checked) } }),
                        Triple("Rest Between Exercises", data.preferences.restBetweenExercises, { checked: Boolean -> vm.settings { it.copy(restBetweenExercises = checked) } }),
                        Triple("Advance within supersets", data.preferences.supersetAutoNext, { checked: Boolean -> vm.settings { it.copy(supersetAutoNext = checked) } }),
                    ).forEach { (label, checked, change) ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = change),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(label, Modifier.weight(1f))
                            Switch(checked, onCheckedChange = null)
                        }
                    }
                    Text("Rest between sides · ${data.preferences.restBetweenSidesPercent}%")
                    Slider(
                        value = data.preferences.restBetweenSidesPercent.toFloat(),
                        onValueChange = { value -> vm.settings { it.copy(restBetweenSidesPercent = value.toInt()) } },
                        valueRange = 0f..100f,
                        modifier = Modifier.semantics { contentDescription = "Rest between sides percentage" },
                    )
                    Text("0% skips side rest; 100% uses the full timer. Myo uses 15 seconds between components.", style = MaterialTheme.typography.bodySmall, color = OF.muted)
                }
            },
            confirmButton = { TextButton({ restSettings = false }) { Text("Done") } },
        )
    if (workoutNotes) SessionNotesDialog("Workout notes", workout.notes, { workoutNotes = false }) {
        vm.workoutNotes(it)
        workoutNotes = false
    }
    if (rename)
        NameDialog(
            "Rename Workout",
            workout.name,
            { rename = false },
            {
                vm.renameActive(it)
                rename = false
            },
        )
    if (confirm == "discard")
        ConfirmDialog(
            "Discard Workout?",
            "This removes the current session and its sets.",
            "Discard",
            { confirm = "" },
            {
                vm.discard()
                confirm = ""
                onBack()
            },
        )
    if (confirm == "finish")
        AlertDialog(
            onDismissRequest = { if (!finishing) confirm = "" },
            title = { Text("Finish Workout?") },
            text = {
                Column {
                    Text("Save ${workout.completedSets()} completed sets to history?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(saveTemplate, { saveTemplate = it }, enabled = !finishing)
                        Text("Save as workout template")
                    }
                    if (finishing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    finishError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton({
                    finishing = true
                    finishError = null
                    val id = workout.id
                    val includeTemplate = saveTemplate
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { vm.finish(includeTemplate) }
                            confirm = ""
                            onFinished(id)
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            finishError = "Could not save workout. Please try again."
                        } finally {
                            finishing = false
                        }
                    }
                }, enabled = !finishing) {
                    Text(if (finishing) "Saving…" else "Finish")
                }
            },
            dismissButton = { TextButton({ confirm = "" }, enabled = !finishing) { Text("Keep Training") } },
        )
}

@Composable
private fun SupersetDialog(workout: Workout, currentId: String, catalog: List<Exercise>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(setOf(currentId)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create superset") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Choose at least two exercises. Existing group memberships for these exercises will be replaced.")
            workout.exercises.forEach { exercise ->
                Row(Modifier.fillMaxWidth().toggleable(exercise.id in selected, role = Role.Checkbox) {
                    selected = if (it) selected + exercise.id else selected - exercise.id
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(exercise.id in selected, null)
                    Text(catalog.find { it.id == exercise.exerciseId }?.title ?: "Exercise", Modifier.weight(1f))
                }
            }
        } },
        confirmButton = { TextButton({ onSave(selected) }, enabled = selected.size >= 2) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SessionNotesDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var notes by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(title) }, minLines = 3) },
        confirmButton = { TextButton({ onSave(notes) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WorkoutTargetsDialog(
    exercise: SessionExercise,
    maxRir: Int,
    onDismiss: () -> Unit,
    onSave: (Map<String, WorkingSetTarget>, Int?) -> Unit,
) {
    val sets = remember { exercise.logicalSets().map { it.root }.filterNot { it.warmup } }
    var values by remember {
        mutableStateOf(sets.associate { it.id to listOf(
            it.targetRepsMin?.toString().orEmpty(),
            it.targetRepsMax?.toString().orEmpty(),
            it.targetRir?.toString().orEmpty(),
        ) })
    }
    var rest by remember { mutableStateOf(exercise.restSeconds?.toString().orEmpty()) }
    val parsed = values.mapValues { (_, fields) ->
        WorkingSetTarget(fields[0].toIntOrNull(), fields[1].toIntOrNull(), fields[2].toIntOrNull())
    }
    val valid = values.values.all { fields ->
        fields.all { it.isBlank() || it.toIntOrNull() != null }
    } && parsed.values.all { it.isValid(maxRir) } &&
        (rest.isBlank() || rest.toIntOrNull()?.let { it in 0..1800 } == true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout Targets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today's targets apply only to this workout. Set minimum/maximum reps and RIR, or leave fields blank for no target.")
                sets.forEachIndexed { index, set ->
                    Text("Working set ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Reps Min" to "Min", "Reps Max" to "Max", "RIR" to "RIR").forEachIndexed { field, (description, label) ->
                            OutlinedTextField(
                                values.getValue(set.id)[field],
                                { text ->
                                    values = values + (set.id to values.getValue(set.id).toMutableList().apply { this[field] = text })
                                },
                                Modifier.weight(1f).semantics {
                                    contentDescription = "Set ${index + 1} $description target"
                                },
                                label = { Text(label) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    rest, { rest = it }, Modifier.fillMaxWidth(),
                    label = { Text("Set Rest Timers (seconds)") },
                    supportingText = { Text("0–1800; blank uses workout settings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (!valid) Text("Use positive ordered rep bounds, RIR 0–$maxRir and rest 0–1800.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton({ onSave(parsed, rest.toIntOrNull()) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetRow(
    index: Int,
    set: LoggedSet,
    prefs: Preferences,
    metrics: List<SetMetric>,
    exerciseName: String,
    onChange: (LoggedSet) -> Unit,
    onComplete: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit = {},
    onKind: (SetKind, Boolean) -> Unit,
    hasExtras: Boolean,
    showTargets: Boolean,
    displayNumber: Int,
    keypad: WorkoutKeypad? = null,
) {
    TraceRecomposition("SetRow", set.id)
    // Stable across recompositions so each field's KeypadField stays equal and unchanged fields skip.
    val latestComplete by rememberUpdatedState(onComplete)
    val stableComplete = remember { { latestComplete() } }
    val focus = LocalFocusManager.current
    var remove by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<Pair<SetKind, Boolean>?>(null) }
    val spokenKind = if (set.warmup) "warmup set" else "set"
    val motionEnabled = LocalAppMotionEnabled.current
    val completionDuration = if (motionEnabled) AppMotion.State else 0
    val completionSource = remember { MutableInteractionSource() }
    val rowColor by animateColorAsState(
        if (set.completed) OF.green.copy(alpha = 0.08f) else Color.Transparent,
        tween(completionDuration, easing = AppMotion.EaseOut), label = "set completion row",
    )
    val completionColor by animateColorAsState(
        if (set.completed) OF.green else OF.elevated,
        tween(completionDuration, easing = AppMotion.EaseOut), label = "set completion surface",
    )
    val checkColor by animateColorAsState(
        if (set.completed) OF.onComplete else if (set.canComplete(metrics)) MaterialTheme.colorScheme.onSurface else OF.muted,
        tween(completionDuration, easing = AppMotion.EaseOut), label = "set completion check color",
    )
    val checkScale by animateFloatAsState(
        if (set.completed || !motionEnabled) 1f else .92f,
        tween(completionDuration, easing = AppMotion.EaseOut), label = "set completion check",
    )
    val canLog = !set.completed && set.canComplete(metrics)
    // With the keypad on this row, a swipe logs exactly like its Log set key; otherwise like the check button.
    val logFromRow = {
        if (keypad != null && keypad.target?.setId == set.id) { if (!keypad.log()) focus.clearFocus() } else onComplete()
    }
    // Clearing focus closes the keypad, so the Undo snackbar never covers its keys.
    val swipe = rememberSetRowSwipe(canLog, logFromRow) { focus.clearFocus(); onDelete() }
    // Swipe is never the only way: TalkBack offers both actions on every control in the row.
    val rowActions = listOfNotNull(
        if (set.completed) CustomAccessibilityAction("Mark set incomplete") { onComplete(); true }
        else if (canLog) CustomAccessibilityAction("Log set") { logFromRow(); true } else null,
        CustomAccessibilityAction("Delete set") { swipe.delete(); true },
    )
    Row(
        Modifier.fillMaxWidth()
            .setSwipeCollapse(swipe)
            .padding(vertical = 4.dp)
            .setRowSwipe(swipe)
            .background(
                rowColor,
                RoundedCornerShape(8.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            TextButton(
                { options = true },
                Modifier.width(40.dp).semantics {
                    contentDescription = "${spokenKind.replaceFirstChar { it.uppercase() }} $displayNumber options for $exerciseName"
                    customActions = rowActions
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(set.side?.label?.take(1) ?: if (set.warmup) "W" else "$displayNumber", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(options, { options = false }) {
                SetKind.entries.forEach { kind -> DropdownMenuItem(
                    text = { Text(kind.label) },
                    trailingIcon = if (set.kind == kind && !set.warmup) { { Icon(Icons.Rounded.Check, null) } } else null,
                    onClick = {
                        options = false
                        if (hasExtras && kind != SetKind.MYO) pendingKind = kind to false else onKind(kind, false)
                    },
                ) }
                DropdownMenuItem(
                    text = { Text(if (set.warmup) "Mark as Working Set" else "Mark as Warm-Up") },
                    onClick = {
                        options = false
                        if (hasExtras) pendingKind = SetKind.STANDARD to !set.warmup else onKind(SetKind.STANDARD, !set.warmup)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (set.segmentOf != null) "Remove Myo component" else "Remove Set") },
                    onClick = {
                        options = false
                        remove = true
                    },
                )
            }
        }
        if (showTargets) Text(
            set.targetDescription().takeUnless { it == "No Target" } ?: "—", Modifier.width(48.dp),
            color = OF.muted, style = MaterialTheme.typography.labelSmall,
        )
        metrics.forEach { metric ->
            key(metric) {
                var text by rememberSaveable(set.id, prefs.pounds, prefs.distanceImperial, metric) {
                    mutableStateOf(metric.display(set, prefs.pounds, prefs.distanceImperial))
                }
                val invalid = text.isNotBlank() && metric.parse(text, prefs.pounds, prefs.distanceImperial) == null
                KeypadTextField(
                    text,
                    { entered ->
                        text = entered
                        val changed = metric.update(set, metric.parse(entered, prefs.pounds, prefs.distanceImperial))
                        onChange(changed.copy(completed = set.completed && changed.canComplete(metrics)))
                    },
                    Modifier.weight(if (metric.isLoad) 1.3f else 1f).semantics {
                        contentDescription = "$exerciseName $spokenKind $displayNumber ${metric.catalogName.lowercase()} (${metric.label(prefs.pounds, prefs.distanceImperial)})"
                        customActions = rowActions
                        if (invalid) error(if (metric.isReps) "Enter a whole number from 1 to 9999" else "Enter a valid ${metric.label(prefs.pounds, prefs.distanceImperial)} value")
                    },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(if (metric.isReps) "Use 1–9999" else "Invalid value") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (metric.isReps) KeyboardType.Number else KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
                    shape = RoundedCornerShape(10.dp),
                    field = keypad?.let { KeypadField(it, set.id, metric, prefs, set.completed, set.canComplete(metrics), stableComplete) },
                )
            }
        }
        IconButton(
            onComplete,
            Modifier.size(48.dp)
                .pressFeedback(completionSource, enabled = set.completed || set.canComplete(metrics))
                .background(completionColor, RoundedCornerShape(8.dp))
                .semantics { customActions = rowActions },
            enabled = set.completed || set.canComplete(metrics),
            interactionSource = completionSource,
        ) {
            Icon(
                Icons.Rounded.Check,
                if (set.completed) "Mark $spokenKind incomplete $displayNumber for $exerciseName"
                else "Complete $spokenKind $displayNumber for $exerciseName",
                modifier = Modifier.graphicsLayer { scaleX = checkScale; scaleY = checkScale },
                tint = checkColor,
            )
        }
    }
    if (remove) ConfirmDialog(
        "Remove set?", "This removes the set and its logged values.", "Remove",
        { remove = false }, { onRemove(); remove = false },
    )
    pendingKind?.let { (kind, warmup) -> ConfirmDialog(
        "Remove Myo components?", "Changing this set type removes its additional component values on both sides. The main set is kept.",
        "Change set type", { pendingKind = null }, { onKind(kind, warmup); pendingKind = null },
    ) }
}
