package dev.bastian.intervalo.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.timer.TimerStatus
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service that keeps a workout alive while the app is in the background or the screen is
 * off. The workout itself lives in [WorkoutRunner]; this class hosts it: it shows the notification,
 * holds a wake lock while the clock is running, routes the notification actions and stops itself
 * when the workout is over.
 */
class TimerService : Service() {

    private val runner: WorkoutRunner by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: WorkoutNotifications
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var sawActive = false
    private var lastKey: Triple<Long, Int, TimerStatus>? = null

    override fun onCreate() {
        super.onCreate()
        notifications = WorkoutNotifications(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "intervalo:workout")
            .apply { setReferenceCounted(false) }
        scope.launch { runner.state.collect(::onState) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground service must call startForeground promptly after being started.
        val current = runner.state.value
        val first = if (current is WorkoutState.Active) notifications.active(current) else notifications.starting()
        try {
            ServiceCompat.startForeground(
                this,
                WorkoutNotifications.NOTIFICATION_ID,
                first,
                // The "specialUse" type only exists from Android 14; earlier versions have no types to declare.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException. It happens when the system restarts the service while the app may not start one. The stored
            // workout is untouched and the app restores it the next time it is opened.
            Log.w(TAG, "Not allowed to run in the foreground right now", e)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START -> {
                val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, 0L)
                scope.launch { if (!runner.start(routineId)) stopSelf() }
            }
            ACTION_TOGGLE_PAUSE -> runner.togglePause()
            ACTION_NEXT -> runner.skipNext()
            ACTION_PREVIOUS -> runner.skipPrevious()
            ACTION_STOP -> runner.stop()
            // No intent: the system restarted us after killing the process. Bring the workout back.
            else -> scope.launch {
                if (!runner.restore() || runner.state.value !is WorkoutState.Active) endService(runner.state.value)
            }
        }
        return START_STICKY
    }

    private fun onState(state: WorkoutState) {
        when (state) {
            is WorkoutState.Active -> {
                sawActive = true
                updateWakeLock(state)
                val key = Triple(state.routine.id, state.timer.phaseIndex, state.timer.status)
                if (key != lastKey) {
                    lastKey = key
                    notificationManager.notify(WorkoutNotifications.NOTIFICATION_ID, notifications.active(state))
                }
            }
            else -> if (sawActive) endService(state)
        }
    }

    private fun endService(state: WorkoutState) {
        sawActive = false
        lastKey = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (state is WorkoutState.Finished) notifications.notifyFinished(state)
        stopSelf()
    }

    /** The CPU must stay awake while the clock runs so cues fire on time with the screen off. */
    private fun updateWakeLock(state: WorkoutState.Active) {
        val lock = wakeLock ?: return
        if (state.timer.status == TimerStatus.RUNNING) {
            // The timeout is only a safety net should the service be lost without releasing.
            lock.acquire(state.timer.totalRemainingMs + WAKE_LOCK_MARGIN_MS)
        } else if (lock.isHeld) {
            lock.release()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TimerService"
        private const val WAKE_LOCK_MARGIN_MS = 60_000L

        const val ACTION_START = "dev.bastian.intervalo.action.START"
        const val ACTION_TOGGLE_PAUSE = "dev.bastian.intervalo.action.TOGGLE_PAUSE"
        const val ACTION_NEXT = "dev.bastian.intervalo.action.NEXT"
        const val ACTION_PREVIOUS = "dev.bastian.intervalo.action.PREVIOUS"
        const val ACTION_STOP = "dev.bastian.intervalo.action.STOP"
        const val EXTRA_ROUTINE_ID = "routine_id"

        fun intent(context: Context, action: String): Intent =
            Intent(context, TimerService::class.java).setAction(action)

        /** Starts a workout of the given routine. Call it while the app is visible or from a user action. */
        fun start(context: Context, routineId: Long) {
            ContextCompat.startForegroundService(
                context,
                intent(context, ACTION_START).putExtra(EXTRA_ROUTINE_ID, routineId),
            )
        }

        /** Starts the service just to bring back a workout persisted before the process died. */
        fun restore(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        }
    }
}
