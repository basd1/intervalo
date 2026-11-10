package dev.bastian.intervalo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.bastian.intervalo.R
import dev.bastian.intervalo.domain.StepType

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
