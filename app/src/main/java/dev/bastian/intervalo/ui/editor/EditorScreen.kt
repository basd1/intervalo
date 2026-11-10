package dev.bastian.intervalo.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.intervalo.R
import dev.bastian.intervalo.domain.MAX_NAME_LENGTH
import dev.bastian.intervalo.domain.MAX_ROUNDS
import dev.bastian.intervalo.domain.MAX_SECONDS
import dev.bastian.intervalo.domain.RoutineError
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.domain.formatDuration
import dev.bastian.intervalo.ui.common.stepDisplayName
import dev.bastian.intervalo.ui.common.stepTypeLabel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val SECONDS_STEP = 5

/** A step being added (`key == null`) or edited in the dialog. */
private data class StepDraft(val key: Long?, val name: String, val type: StepType, val seconds: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    routineId: Long,
    onBack: () -> Unit,
    viewModel: EditorViewModel = koinViewModel(key = "editor-$routineId", parameters = { parametersOf(routineId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val listState = rememberLazyListState()
    val reorder = remember(listState) { ReorderState(listState, viewModel::moveStep) }
    var draft by remember { mutableStateOf<StepDraft?>(null) }
    val validation = state.validation

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = !state.isLoading) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.notFound) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.routine_not_found))
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "name") {
                val nameError = validation?.errors?.let {
                    when {
                        RoutineError.NAME_BLANK in it -> R.string.error_name_blank
                        RoutineError.NAME_TOO_LONG in it -> R.string.error_name_too_long
                        else -> null
                    }
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = {
                        Text(
                            if (nameError != null) stringResource(nameError, MAX_NAME_LENGTH) else "",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "colors") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoutineColors.palette.forEach { color ->
                        ColorDot(color = color, selected = color == state.color, onClick = { viewModel.setColor(color) })
                    }
                }
            }
            item(key = "warmup") {
                Stepper(
                    label = stringResource(R.string.field_warmup),
                    value = formatDuration(state.warmupSeconds),
                    onMinus = { viewModel.setWarmup(state.warmupSeconds - SECONDS_STEP) },
                    onPlus = { viewModel.setWarmup(state.warmupSeconds + SECONDS_STEP) },
                    minusEnabled = state.warmupSeconds > 0,
                    plusEnabled = state.warmupSeconds < MAX_SECONDS,
                )
            }
            item(key = "cooldown") {
                Stepper(
                    label = stringResource(R.string.field_cooldown),
                    value = formatDuration(state.cooldownSeconds),
                    onMinus = { viewModel.setCooldown(state.cooldownSeconds - SECONDS_STEP) },
                    onPlus = { viewModel.setCooldown(state.cooldownSeconds + SECONDS_STEP) },
                    minusEnabled = state.cooldownSeconds > 0,
                    plusEnabled = state.cooldownSeconds < MAX_SECONDS,
                )
            }
            item(key = "rounds") {
                Stepper(
                    label = stringResource(R.string.field_rounds),
                    value = state.rounds.toString(),
                    onMinus = { viewModel.setRounds(state.rounds - 1) },
                    onPlus = { viewModel.setRounds(state.rounds + 1) },
                    minusEnabled = state.rounds > 1,
                    plusEnabled = state.rounds < MAX_ROUNDS,
                )
            }
            item(key = "stepsHeader") {
                Column {
                    Text(stringResource(R.string.steps_header), style = MaterialTheme.typography.titleMedium)
                    if (validation != null && RoutineError.NO_STEPS in validation.errors) {
                        Text(
                            stringResource(R.string.error_no_steps),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        stringResource(R.string.steps_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(state.steps, key = { it.key }) { step ->
                val dragged = reorder.draggedKey == step.key
                val index = state.steps.indexOf(step)
                val rowModifier = if (dragged) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }
                } else {
                    Modifier.animateItem()
                }
                val moveUp = stringResource(R.string.move_up)
                val moveDown = stringResource(R.string.move_down)
                StepRow(
                    modifier = rowModifier.semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(moveUp) { viewModel.moveStepBy(step.key, -1); true },
                            CustomAccessibilityAction(moveDown) { viewModel.moveStepBy(step.key, 1); true },
                        )
                    },
                    name = stepDisplayName(step.name, step.type),
                    typeLabel = stepTypeLabel(step.type),
                    seconds = step.durationSeconds,
                    isWork = step.type == StepType.WORK,
                    invalid = validation != null && index in validation.invalidStepIndices,
                    dragHandle = Modifier.pointerInput(step.key) {
                        detectDragGestures(
                            onDragStart = { reorder.start(step.key) },
                            onDrag = { change, amount ->
                                change.consume()
                                reorder.drag(amount.y)
                            },
                            onDragEnd = reorder::end,
                            onDragCancel = reorder::end,
                        )
                    },
                    onEdit = { draft = StepDraft(step.key, step.name, step.type, step.durationSeconds) },
                    onDelete = { viewModel.removeStep(step.key) },
                )
            }
            item(key = "addButtons") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { draft = StepDraft(null, "", StepType.WORK, 30) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_work), modifier = Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onClick = { draft = StepDraft(null, "", StepType.REST, 15) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_rest), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    draft?.let { current ->
        StepDialog(
            initial = current,
            onDismiss = { draft = null },
            onConfirm = { name, type, seconds ->
                if (current.key == null) viewModel.addStep(name, type, seconds)
                else viewModel.updateStep(current.key, name, type, seconds)
                draft = null
            },
        )
    }
}

@Composable
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.color_option)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                else Modifier,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onMinus, enabled = minusEnabled) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease, label))
        }
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = onPlus, enabled = plusEnabled) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase, label))
        }
    }
}

@Composable
private fun StepRow(
    modifier: Modifier,
    name: String,
    typeLabel: String,
    seconds: Int,
    isWork: Boolean,
    invalid: Boolean,
    dragHandle: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.drag_to_reorder),
                modifier = dragHandle.padding(12.dp),
            )
            Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$typeLabel · ${formatDuration(seconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (invalid) MaterialTheme.colorScheme.error
                    else if (isWork) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_step))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_step))
            }
        }
    }
}

@Composable
private fun StepDialog(
    initial: StepDraft,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: StepType, seconds: Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var secondsText by rememberSaveable { mutableStateOf(initial.seconds.toString()) }
    val seconds = secondsText.toIntOrNull()
    val secondsValid = seconds != null && seconds in 1..MAX_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.key == null) R.string.add_step_title else R.string.edit_step)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(stepTypeLabel(option)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.field_step_name)) },
                    placeholder = { Text(stepTypeLabel(type)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.field_seconds)) },
                    isError = !secondsValid,
                    supportingText = { if (!secondsValid) Text(stringResource(R.string.error_seconds, MAX_SECONDS)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(enabled = secondsValid, onClick = { onConfirm(name, type, seconds ?: 0) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
