package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the swap sheet, the "Just today / also update" choice and the commit. A pick from the
 * shared search route arrives as [searchPick] and takes the same path as a suggestion.
 */
@Composable
internal fun ExerciseSwapHost(
    data: AppData,
    workout: Workout,
    exercises: List<Exercise>,
    vm: WorkoutViewModel,
    sheetEntryId: String?,
    onCloseSheet: () -> Unit,
    searchPick: Pair<String, String>?,
    onSearchPickHandled: () -> Unit,
    onSearchAll: ((String) -> Unit)?,
    onSwapped: (undo: ExerciseSwapUndo, replacementTitle: String) -> Unit,
) {
    var choiceEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var choiceReplacement by rememberSaveable { mutableStateOf<String?>(null) }
    // After a commit the session's sheet is already closed; this keeps it composed while it slides
    // away. Reopening during that slide brings the same sheet back instead of losing the tap.
    var lingering by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sheetEntryId) { if (sheetEntryId != null) lingering = null }
    val shownId = sheetEntryId ?: lingering
    val closing = sheetEntryId == null && lingering != null
    fun title(id: String) = exercises.find { it.id == id }?.title ?: "Exercise"
    fun commit(entryId: String, replacementId: String, scope: SwapScope) {
        choiceEntry = null
        choiceReplacement = null
        val undo = vm.swapExercise(entryId, replacementId, scope)
        // The sheet slides away over the new block instead of vanishing.
        if (sheetEntryId != null) { lingering = sheetEntryId; onCloseSheet() }
        if (undo != null) onSwapped(undo, title(replacementId))
    }
    fun request(entryId: String, replacementId: String) {
        if (vm.data.value.swapPlanFor(entryId, replacementId) == null) commit(entryId, replacementId, SwapScope.TODAY)
        else { choiceEntry = entryId; choiceReplacement = replacementId }
    }
    LaunchedEffect(searchPick) {
        val (entryId, replacementId) = searchPick ?: return@LaunchedEffect
        onSearchPickHandled()
        if (workout.exercises.any { it.id == entryId }) request(entryId, replacementId)
    }
    shownId?.let { id ->
        // The workout as it was when opened: a committed swap must not reshuffle the closing list,
        // but a reopen during the slide starts from the current workout.
        var opened by remember(id) { mutableStateOf(workout) }
        LaunchedEffect(id, sheetEntryId) { if (sheetEntryId != null) opened = workout }
        val entry = opened.exercises.find { it.id == id }
        val original = entry?.let { e -> exercises.find { it.id == e.exerciseId } }
        if (entry == null || original == null) LaunchedEffect(id) { onCloseSheet() }
        else ExerciseSwapSheet(
            data, opened, entry, original, exercises, vm, closing,
            onDismiss = { if (sheetEntryId == null) lingering = null else onCloseSheet() },
            onChoose = { if (!closing) request(entry.id, it) },
            onSearchAll = onSearchAll?.let { search -> { onCloseSheet(); search(entry.id) } },
        )
    }
    val entryId = choiceEntry
    val replacementId = choiceReplacement
    val plan = if (entryId != null && replacementId != null) data.swapPlanFor(entryId, replacementId) else null
    if (entryId != null && replacementId != null) {
        if (plan == null) LaunchedEffect(entryId, replacementId) { choiceEntry = null; choiceReplacement = null }
        else SwapScopeDialog(
            plan, title(workout.exercises.find { it.id == entryId }?.exerciseId.orEmpty()), title(replacementId),
            onDismiss = { choiceEntry = null; choiceReplacement = null },
            onToday = { commit(entryId, replacementId, SwapScope.TODAY) },
            onPlan = { commit(entryId, replacementId, plan.scope) },
        )
    }
}

@Composable
private fun SwapScopeDialog(
    plan: SwapPlan,
    originalTitle: String,
    replacementTitle: String,
    onDismiss: () -> Unit,
    onToday: () -> Unit,
    onPlan: () -> Unit,
) {
    val noun = if (plan.scope == SwapScope.PROGRAM) "program" else "template"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap for $replacementTitle") },
        text = {
            Text(
                if (plan.alreadyContainsReplacement)
                    "“${plan.name}” already includes $replacementTitle, so only today’s workout will change."
                else "“${plan.name}” includes $originalTitle. Swap just for today, or use $replacementTitle in the $noun from now on?"
            )
        },
        confirmButton = {
            if (!plan.alreadyContainsReplacement) TextButton(onPlan) { Text("Also update $noun") }
            else TextButton(onToday) { Text("Just today") }
        },
        dismissButton = {
            if (!plan.alreadyContainsReplacement) TextButton(onToday) { Text("Just today") }
            else TextButton(onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseSwapSheet(
    data: AppData,
    workout: Workout,
    entry: SessionExercise,
    original: Exercise,
    exercises: List<Exercise>,
    vm: WorkoutViewModel,
    closing: Boolean,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    onSearchAll: (() -> Unit)?,
) {
    val motion = LocalAppMotionEnabled.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(closing) {
        if (closing) {
            sheetState.hide()
            onDismiss()
        } else if (!sheetState.isVisible) sheetState.show()
    }
    var guideId by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    val gym = data.gyms.find { it.id == workout.gymId } ?: data.gyms.firstOrNull()
    val excluded = remember(workout.exercises) { workout.exercises.mapTo(HashSet()) { it.exerciseId } }
    val doNotRecommend = remember(data.exerciseDefaults) { data.exerciseDefaults.filterValues { it.doNotRecommend }.keys }
    val equipment = vm.equipmentCatalog
    // The full catalog is scored on a worker; the shared index supplies a cached tiebreak order.
    val suggestions by produceState<List<SwapSuggestion>?>(null, original.id, excluded, gym, data.favorites, doNotRecommend, data.history, exercises) {
        value = withContext(Dispatchers.Default) {
            rankSwapAlternatives(
                original, ExerciseSearchIndexes.forExercises(exercises).search(""), excluded,
                data.favorites, data.history.recentExerciseIds(), doNotRecommend,
                available = { equipment.available(it, gym) },
            )
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OF.background,
    ) {
        Box(Modifier.fillMaxHeight(.95f)) {
            // Back from a suggestion's guide returns to the list instead of closing the whole sheet.
            BackHandler(guideId != null) { guideId = null }
            Crossfade(guideId, animationSpec = tween(if (motion) AppMotion.State else 0, easing = AppMotion.EaseOut), label = "swap sheet content") { id ->
                val guided = id?.let { g -> exercises.find { it.id == g } }
                if (guided != null) Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        ExerciseDetail(guided, data, vm, onBack = { guideId = null }, onAdd = null, closeable = true)
                    }
                    Box(Modifier.padding(18.dp)) {
                        PrimaryAction("Swap for ${guided.title}", { onChoose(guided.id) }, icon = Icons.Rounded.SwapHoriz)
                    }
                } else SwapSuggestionList(entry, original, gym, suggestions, onChoose, { guideId = it }, onSearchAll)
            }
        }
    }
}

@Composable
private fun SwapSuggestionList(
    entry: SessionExercise,
    original: Exercise,
    gym: GymProfile?,
    suggestions: List<SwapSuggestion>?,
    onChoose: (String) -> Unit,
    onGuide: (String) -> Unit,
    onSearchAll: (() -> Unit)?,
) {
    val motion = LocalAppMotionEnabled.current
    // One fade when results first arrive; recomposition never replays it.
    val reveal = remember { Animatable(if (motion && suggestions == null) 0f else 1f) }
    LaunchedEffect(suggestions != null) {
        if (suggestions != null && reveal.value < 1f) reveal.animateTo(1f, tween(AppMotion.Quick, easing = AppMotion.EaseOut))
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Swap exercise", style = MaterialTheme.typography.titleLarge)
            Text("Replacing ${original.title}", style = MaterialTheme.typography.bodyLarge, color = OF.muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.sets.any { it.completed }) Text(
                "Completed sets stay with ${original.title}. Remaining sets move to the new exercise.",
                style = MaterialTheme.typography.bodySmall, color = OF.muted,
            )
        }
        LazyColumn(
            Modifier.weight(1f).graphicsLayer { alpha = reveal.value },
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            val list = suggestions
            if (list != null && list.isEmpty()) item {
                EmptyContent(
                    Icons.Rounded.SearchOff,
                    "No close matches",
                    if (gym != null) "Nothing else at ${gym.name} trains ${original.muscle.lowercase()} the same way. Search all exercises to choose any movement."
                    else "Search all exercises to choose any movement.",
                    if (onSearchAll != null) "Search all exercises" else null,
                    onSearchAll ?: {},
                )
            }
            if (!list.isNullOrEmpty()) item {
                Text(if (gym != null) "Suggested for ${gym.name}" else "Suggested", Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge, color = OF.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            items(list.orEmpty(), key = { it.exercise.id }) { suggestion ->
                SwapCandidateRow(suggestion, { onChoose(suggestion.exercise.id) }, { onGuide(suggestion.exercise.id) })
                HorizontalDivider(Modifier.padding(start = 60.dp), color = OF.line.copy(alpha = 0.5f))
            }
        }
        if (onSearchAll != null && suggestions?.isEmpty() != true) {
            val source = remember { MutableInteractionSource() }
            OutlinedButton(
                onSearchAll,
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).heightIn(min = 48.dp).pressFeedback(source),
                interactionSource = source,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search all exercises")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwapCandidateRow(suggestion: SwapSuggestion, onChoose: () -> Unit, onGuide: () -> Unit) {
    val e = suggestion.exercise
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().testTag("swap-suggestion")
            .pressFeedback(source)
            .clickable(source, LocalIndication.current, onClickLabel = "Swap for ${e.title}", role = Role.Button, onClick = onChoose)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExerciseImage(e, Modifier.width(46.dp).height(62.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${e.muscle} · ${e.equipment}", style = MaterialTheme.typography.bodySmall, color = OF.muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (suggestion.reasons.isNotEmpty()) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                suggestion.reasons.take(3).forEach { reason ->
                    Surface(color = OF.elevated, shape = RoundedCornerShape(50)) {
                        Text(reason.label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        val guide = remember { MutableInteractionSource() }
        TextButton(onGuide, Modifier.heightIn(min = 48.dp).pressFeedback(guide).semantics { contentDescription = "Guide for ${e.title}" }, interactionSource = guide) {
            Text("Guide")
        }
    }
}
