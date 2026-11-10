package dev.bastian.intervalo.ui.routines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.bastian.intervalo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.routines_title)) }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.routines_empty))
        }
    }
}
