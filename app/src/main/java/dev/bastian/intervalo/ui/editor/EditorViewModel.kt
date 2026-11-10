package dev.bastian.intervalo.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.intervalo.domain.MAX_ROUNDS
import dev.bastian.intervalo.domain.MAX_SECONDS
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.RoutineValidation
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.domain.validateRoutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A step in the editor. The key identifies it while it is dragged around, before it has a database id. */
data class EditableStep(
    val key: Long,
    val name: String,
    val type: StepType,
    val durationSeconds: Int,
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val color: Int = RoutineColors.palette.first(),
    val warmupSeconds: Int = 30,
    val cooldownSeconds: Int = 30,
    val rounds: Int = 5,
    val steps: List<EditableStep> = emptyList(),
    /** Null until the user tries to save, so a fresh form does not start full of errors. */
    val validation: RoutineValidation? = null,
    val saved: Boolean = false,
)

object RoutineColors {
    val palette: List<Int> = listOf(
        0xFFE65100.toInt(),
        0xFF1565C0.toInt(),
        0xFF2E7D32.toInt(),
        0xFF6A1B9A.toInt(),
        0xFFC62828.toInt(),
        0xFF00838F.toInt(),
    )
}

/** Editor for a new routine (`routineId == 0`) or an existing one. */
class EditorViewModel(
    private val routineId: Long,
    private val repository: RoutineRepository,
) : ViewModel() {

    private var nextKey = 1L
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        if (routineId == 0L) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    steps = listOf(
                        newStep("", StepType.WORK, 30),
                        newStep("", StepType.REST, 15),
                    ),
                )
            }
        } else {
            viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        val routine = repository.get(routineId)
        if (routine == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = false,
                name = routine.name,
                color = routine.color,
                warmupSeconds = routine.warmupSeconds,
                cooldownSeconds = routine.cooldownSeconds,
                rounds = routine.rounds,
                steps = routine.steps.map { step -> newStep(step.name, step.type, step.durationSeconds) },
            )
        }
    }

    private fun newStep(name: String, type: StepType, seconds: Int) = EditableStep(nextKey++, name, type, seconds)

    fun setName(name: String) = change { it.copy(name = name) }

    fun setColor(color: Int) = change { it.copy(color = color) }

    fun setWarmup(seconds: Int) = change { it.copy(warmupSeconds = seconds.coerceIn(0, MAX_SECONDS)) }

    fun setCooldown(seconds: Int) = change { it.copy(cooldownSeconds = seconds.coerceIn(0, MAX_SECONDS)) }

    fun setRounds(rounds: Int) = change { it.copy(rounds = rounds.coerceIn(1, MAX_ROUNDS)) }

    fun addStep(name: String, type: StepType, seconds: Int) =
        change { it.copy(steps = it.steps + newStep(name, type, seconds)) }

    fun updateStep(key: Long, name: String, type: StepType, seconds: Int) = change { state ->
        state.copy(steps = state.steps.map { if (it.key == key) it.copy(name = name, type = type, durationSeconds = seconds) else it })
    }

    fun removeStep(key: Long) = change { state -> state.copy(steps = state.steps.filterNot { it.key == key }) }

    /** Moves the step `fromKey` to the position currently held by `toKey`. */
    fun moveStep(fromKey: Long, toKey: Long) = change { state ->
        val steps = state.steps.toMutableList()
        val from = steps.indexOfFirst { it.key == fromKey }
        val to = steps.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return@change state
        steps.add(to, steps.removeAt(from))
        state.copy(steps = steps)
    }

    /** Moves a step one place up (`delta = -1`) or down (`delta = 1`); used by accessibility actions. */
    fun moveStepBy(key: Long, delta: Int) {
        val steps = _uiState.value.steps
        val from = steps.indexOfFirst { it.key == key }
        val target = steps.getOrNull(from + delta) ?: return
        moveStep(key, target.key)
    }

    fun save() {
        val state = _uiState.value
        val routine = state.toRoutine()
        val validation = validateRoutine(routine)
        if (!validation.isValid) {
            _uiState.update { it.copy(validation = validation) }
            return
        }
        viewModelScope.launch {
            repository.save(routine)
            _uiState.update { it.copy(saved = true, validation = validation) }
        }
    }

    private fun EditorUiState.toRoutine() = Routine(
        id = routineId,
        name = name.trim(),
        color = color,
        warmupSeconds = warmupSeconds,
        cooldownSeconds = cooldownSeconds,
        rounds = rounds,
        steps = steps.map { Step(it.name.trim(), it.type, it.durationSeconds) },
    )

    /** Applies an edit and, once the user has tried to save, refreshes the visible validation. */
    private fun change(transform: (EditorUiState) -> EditorUiState) {
        _uiState.update { current ->
            val next = transform(current)
            if (next.validation == null) next else next.copy(validation = validateRoutine(next.toRoutine()))
        }
    }
}
