package dev.bastian.intervalo.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Home screen widget. Glance keeps the composition of a widget alive for a while, so the content
 * follows [WidgetModels] (which only emits on phase or state changes) instead of being a snapshot.
 * [WidgetUpdater] additionally asks for an update so that a widget whose session has ended is redrawn.
 */
class IntervaloWidget : GlanceAppWidget(), KoinComponent {
    private val models: WidgetModels by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = models.observe()
        val initial = flow.first()
        provideContent {
            val model by flow.collectAsState(initial)
            GlanceTheme { WidgetContent(model) }
        }
    }
}

class IntervaloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IntervaloWidget()
}
