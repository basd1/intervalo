package dev.bastian.intervalo.ui.routines

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.intervalo.R
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.formatDuration
import dev.bastian.intervalo.service.TimerService
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onNewRoutine: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    onOpenWorkout: () -> Unit,
    viewModel: RoutinesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Routine?>(null) }
    var pendingStart by remember { mutableStateOf<Routine?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    fun begin(routine: Routine) {
        TimerService.start(context, routine.id)
        onOpenWorkout()
    }

    // The workout runs whether or not the user allows notifications, so the answer only decides what is shown.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStart?.let(::begin)
        pendingStart = null
    }

    fun requestStart(routine: Routine) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (state.activeRoutineName != null) {
            onOpenWorkout()
        } else if (needsPermission && !viewModel.notificationPromptShown) {
            viewModel.markNotificationPromptShown()
            pendingStart = routine
            showPermissionRationale = true
        } else {
            begin(routine)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.routines_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewRoutine) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.routines_new))
            }
        },
    ) { padding ->
        if (state.isLoaded && state.routines.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.routines_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            ) {
                state.activeRoutineName?.let { name ->
                    item(key = "active") { ActiveBanner(name = name, onOpen = onOpenWorkout) }
                }
                items(state.routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onEdit = { onEditRoutine(routine.id) },
                        onStart = { requestStart(routine) },
                        onDuplicate = { viewModel.duplicate(routine, copyName = it) },
                        onDelete = { pendingDelete = routine },
                    )
                }
            }
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { Text(stringResource(R.string.notification_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.notification_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    pendingStart?.let(::begin)
                    pendingStart = null
                }) { Text(stringResource(R.string.notification_permission_skip)) }
            },
        )
    }

    pendingDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_routine_title)) },
            text = { Text(stringResource(R.string.delete_routine_message, routine.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(routine)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ActiveBanner(name: String, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workout_in_progress), style = MaterialTheme.typography.titleMedium)
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: (copyName: String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val copyName = stringResource(R.string.copy_name, routine.name)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onStart)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(6.dp)
                    .height(48.dp)
                    .background(Color(routine.color), RoundedCornerShape(3.dp)),
            )
            Box(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.routine_summary,
                        formatDuration(routine.totalSeconds),
                        pluralStringResource(R.plurals.rounds_count, routine.rounds, routine.rounds),
                        pluralStringResource(R.plurals.steps_count, routine.steps.size, routine.steps.size),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStart) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.start_routine, routine.name),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        onClick = { menuOpen = false; onDuplicate(copyName) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}
