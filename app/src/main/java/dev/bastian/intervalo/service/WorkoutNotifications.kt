package dev.bastian.intervalo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.StringRes
import dev.bastian.intervalo.MainActivity
import dev.bastian.intervalo.R
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.formatDuration
import dev.bastian.intervalo.ui.common.phaseLabel

/** Builds the ongoing workout notification and the "finished" one. */
class WorkoutNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKOUT,
                context.getString(R.string.channel_workout),
                // Low: the notification must not make a sound of its own; the cues come from the app.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_workout_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                context.getString(R.string.channel_done),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /** Shown for the instant between the service starting and the workout state being known. */
    fun starting(): Notification =
        base(CHANNEL_WORKOUT).setContentTitle(context.getString(R.string.app_name)).build()

    fun active(state: WorkoutState.Active): Notification {
        val timer = state.timer
        val phase = timer.phase
        val title = if (phase.round > 0) {
            context.getString(R.string.notification_phase_round, context.phaseLabel(phase), phase.round, state.routine.rounds)
        } else {
            context.phaseLabel(phase)
        }
        val next = timer.nextPhase?.let { context.getString(R.string.notification_next, context.phaseLabel(it)) }
            ?: context.getString(R.string.notification_last_phase)
        val builder = base(CHANNEL_WORKOUT)
            .setContentTitle(title)
            .setColor(state.routine.color)
            .setOngoing(true)
            .addAction(
                action(
                    if (timer.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    if (timer.isPaused) R.string.action_resume else R.string.action_pause,
                    TimerService.ACTION_TOGGLE_PAUSE,
                ),
            )
            .addAction(action(android.R.drawable.ic_media_next, R.string.action_skip, TimerService.ACTION_NEXT))
            .addAction(
                action(android.R.drawable.ic_menu_close_clear_cancel, R.string.action_stop, TimerService.ACTION_STOP),
            )
        if (timer.isPaused) {
            builder.setContentText(context.getString(R.string.notification_paused, state.routine.name, formatDuration(((timer.phaseRemainingMs + 999) / 1000).toInt())))
                .setShowWhen(false)
        } else {
            // The system counts down natively: no need to update the notification every second.
            builder.setContentText("${state.routine.name} · $next")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + timer.phaseRemainingMs)
        }
        return builder.build()
    }

    fun notifyFinished(state: WorkoutState.Finished) {
        val notification = base(CHANNEL_DONE)
            .setContentTitle(context.getString(R.string.notification_done_title))
            .setContentText(
                context.getString(R.string.notification_done_text, state.routine.name, formatDuration(state.activeSeconds)),
            )
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_DONE_ID, notification)
    }

    private fun action(icon: Int, @StringRes title: Int, serviceAction: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, icon),
            context.getString(title),
            serviceIntent(serviceAction),
        ).build()

    private fun base(channel: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .apply {
                // From Android 12 the system may delay a foreground notification unless told not to.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        TimerService.intent(context, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_WORKOUT = "workout"
        const val CHANNEL_DONE = "workout_done"
        const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_DONE_ID = 2
        private const val REQUEST_OPEN = 100
    }
}
