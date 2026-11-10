package dev.bastian.intervalo.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.Button
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.LocalContext
import dev.bastian.intervalo.MainActivity
import dev.bastian.intervalo.R
import dev.bastian.intervalo.service.TimerService
import dev.bastian.intervalo.ui.common.phaseLabel
import java.util.Date

/** The widget layout. Kept apart from the widget class so tests can render it with a given model. */
@Composable
fun WidgetContent(model: WidgetModel) {
    val context = LocalContext.current
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, model is WidgetModel.Active)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(openApp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (model) {
            is WidgetModel.Idle -> IdleContent(context, model)
            is WidgetModel.Active -> ActiveContent(context, model)
        }
    }
}

@Composable
private fun IdleContent(context: Context, model: WidgetModel.Idle) {
    Text(
        text = model.routineName ?: context.getString(R.string.app_name),
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
        maxLines = 1,
    )
    Text(
        text = context.getString(if (model.routineId != null) R.string.widget_ready else R.string.widget_no_routine),
        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
    )
    Spacer(GlanceModifier.height(8.dp))
    if (model.routineId != null) {
        Button(
            text = context.getString(R.string.widget_start),
            onClick = actionStartService(
                TimerService.intent(context, TimerService.ACTION_START).putExtra(TimerService.EXTRA_ROUTINE_ID, model.routineId),
                isForegroundService = true,
            ),
        )
    }
}

@Composable
private fun ActiveContent(context: Context, model: WidgetModel.Active) {
    Text(
        text = model.routineName,
        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
        maxLines = 1,
    )
    Text(
        text = context.phaseLabel(model.phase),
        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
        maxLines = 1,
    )
    Text(
        text = model.endsAtWall?.let {
            context.getString(R.string.widget_ends_at, DateFormat.getTimeFormat(context).format(Date(it)))
        } ?: context.getString(R.string.paused),
        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
        modifier = GlanceModifier.fillMaxWidth(),
    )
    Spacer(GlanceModifier.height(8.dp))
    Button(
        text = context.getString(if (model.paused) R.string.action_resume else R.string.action_pause),
        onClick = actionStartService(
            TimerService.intent(context, TimerService.ACTION_TOGGLE_PAUSE),
            isForegroundService = true,
        ),
    )
}
