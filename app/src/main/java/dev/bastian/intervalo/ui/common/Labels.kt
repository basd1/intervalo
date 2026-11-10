package dev.bastian.intervalo.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.bastian.intervalo.R
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.domain.timer.Phase
import dev.bastian.intervalo.domain.timer.PhaseKind

@Composable
fun stepTypeLabel(type: StepType): String = stringResource(
    when (type) {
        StepType.WORK -> R.string.step_work
        StepType.REST -> R.string.step_rest
    },
)

/** The name a step shows: its own label, or the default one of its type. */
@Composable
fun stepDisplayName(name: String, type: StepType): String = name.ifBlank { stepTypeLabel(type) }

@StringRes
private fun defaultLabel(kind: PhaseKind): Int = when (kind) {
    PhaseKind.WARMUP -> R.string.phase_warmup
    PhaseKind.WORK -> R.string.step_work
    PhaseKind.REST -> R.string.step_rest
    PhaseKind.COOLDOWN -> R.string.phase_cooldown
}

/** Name of a phase for the UI: the routine's own label if it has one, else the default for its kind. */
@Composable
fun phaseLabel(phase: Phase): String = phase.name.ifBlank { stringResource(defaultLabel(phase.kind)) }

fun Context.phaseLabel(phase: Phase): String = phase.name.ifBlank { getString(defaultLabel(phase.kind)) }
