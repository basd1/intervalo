package dev.bastian.intervalo.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Redraws the widget when something it shows changes: workout started, phase changed, paused, ended. */
class WidgetUpdater(
    private val context: Context,
    private val models: WidgetModels,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            models.observe().collect { IntervaloWidget().updateAll(context) }
        }
    }
}
