package dev.bastian.intervalo.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.intervalo.R
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.formatDuration
import dev.bastian.intervalo.domain.timer.PhaseKind
import dev.bastian.intervalo.domain.timer.TimerState
import dev.bastian.intervalo.ui.common.phaseLabel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/** How long the screen waits for a workout to appear before giving up and going back. */
private const val START_GRACE_MS = 4_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onClose: () -> Unit,
    viewModel: WorkoutViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workout = state.workout
    var hasShownWorkout by remember { mutableStateOf(false) }
    if (workout !is WorkoutState.Idle) hasShownWorkout = true

    // Idle after a workout was shown means it was stopped; Idle from the start means it never began.
    LaunchedEffect(workout is WorkoutState.Idle, hasShownWorkout) {
        if (workout is WorkoutState.Idle) {
            if (!hasShownWorkout) delay(START_GRACE_MS)
            onClose()
        }
    }

    val view = LocalView.current
    val keepOn = state.keepScreenOn && workout is WorkoutState.Active
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (workout) {
                            is WorkoutState.Active -> workout.routine.name
                            is WorkoutState.Finished -> workout.routine.name
                            WorkoutState.Idle -> stringResource(R.string.workout_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (workout) {
                WorkoutState.Idle -> CircularProgressIndicator()
                is WorkoutState.Active -> ActiveWorkout(
                    timer = workout.timer,
                    rounds = workout.routine.rounds,
                    onTogglePause = viewModel::togglePause,
                    onNext = viewModel::skipNext,
                    onPrevious = viewModel::skipPrevious,
                    onStop = viewModel::stop,
                )
                is WorkoutState.Finished -> FinishedWorkout(workout, onDone = {
                    viewModel.dismissResult()
                })
            }
        }
    }
}

@Composable
private fun ActiveWorkout(
    timer: TimerState,
    rounds: Int,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
) {
    val color = phaseColor(timer.phase.kind)
    val label = phaseLabel(timer.phase)
    val secondsLeft = ((timer.phaseRemainingMs + 999) / 1000).toInt()
    val remainingText = formatDuration(secondsLeft)
    val ringDescription = stringResource(R.string.ring_description, label, remainingText)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(label, style = MaterialTheme.typography.displaySmall, color = color)
        if (timer.phase.round > 0) {
            Text(
                stringResource(R.string.round_of, timer.phase.round, rounds),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ProgressRing(
            remainingFraction = 1f - timer.phaseProgress,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .aspectRatio(1f)
                .semantics { contentDescription = ringDescription },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    remainingText,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                )
                if (timer.isPaused) {
                    Text(stringResource(R.string.paused), style = MaterialTheme.typography.titleMedium, color = color)
                }
            }
        }

        val next = timer.nextPhase
        Text(
            text = if (next != null) stringResource(R.string.next_phase, phaseLabel(next), formatDuration((next.durationMs / 1000).toInt()))
            else stringResource(R.string.last_phase),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.total_remaining, formatDuration(((timer.totalRemainingMs + 999) / 1000).toInt())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.action_previous), modifier = Modifier.size(32.dp))
            }
            FilledIconButton(
                onClick = onTogglePause,
                modifier = Modifier.size(88.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
            ) {
                Icon(
                    if (timer.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (timer.isPaused) R.string.action_resume else R.string.action_pause),
                    modifier = Modifier.size(48.dp),
                )
            }
            FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.action_skip), modifier = Modifier.size(32.dp))
            }
        }
        OutlinedButton(onClick = onStop) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ProgressRing(
    remainingFraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * remainingFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Box(Modifier.clearAndSetSemantics { }) { content() }
    }
}

@Composable
private fun phaseColor(kind: PhaseKind): Color = when (kind) {
    PhaseKind.WORK -> MaterialTheme.colorScheme.primary
    PhaseKind.REST -> MaterialTheme.colorScheme.tertiary
    PhaseKind.WARMUP, PhaseKind.COOLDOWN -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun FinishedWorkout(state: WorkoutState.Finished, onDone: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.workout_complete), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.workout_complete_summary, state.routine.name, formatDuration(state.activeSeconds)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDone) { Text(stringResource(R.string.done)) }
    }
}
