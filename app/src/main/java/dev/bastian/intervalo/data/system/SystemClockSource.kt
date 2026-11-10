package dev.bastian.intervalo.data.system

import android.os.SystemClock
import dev.bastian.intervalo.domain.Clock

class SystemClockSource : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
