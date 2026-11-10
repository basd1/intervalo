package dev.bastian.intervalo.data.system

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.bastian.intervalo.domain.PhaseFeedback
import dev.bastian.intervalo.domain.Settings
import dev.bastian.intervalo.domain.timer.PhaseKind

/** Beeps with [ToneGenerator] and vibrates, each only if the settings allow it. */
class AndroidPhaseFeedback(context: Context) : PhaseFeedback {

    private val tone: ToneGenerator? by lazy {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
        } catch (e: RuntimeException) {
            // The audio system can refuse a new tone generator (for example when out of resources).
            Log.w(TAG, "Sound cues unavailable", e)
            null
        }
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun phaseStarted(kind: PhaseKind, settings: Settings) {
        val isWork = kind == PhaseKind.WORK
        beep(settings, if (isWork) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 350)
        vibrate(settings, if (isWork) longArrayOf(0, 120, 80, 120) else longArrayOf(0, 250))
    }

    override fun countdownTick(settings: Settings) {
        beep(settings, ToneGenerator.TONE_PROP_BEEP, 120)
        vibrate(settings, longArrayOf(0, 40))
    }

    override fun finished(settings: Settings) {
        beep(settings, ToneGenerator.TONE_PROP_ACK, 700)
        vibrate(settings, longArrayOf(0, 300, 150, 300, 150, 600))
    }

    private fun beep(settings: Settings, toneType: Int, durationMs: Int) {
        if (settings.sounds) tone?.startTone(toneType, durationMs)
    }

    private fun vibrate(settings: Settings, pattern: LongArray) {
        if (settings.vibration) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private companion object {
        const val TAG = "PhaseFeedback"
        const val TONE_VOLUME = 90
    }
}
