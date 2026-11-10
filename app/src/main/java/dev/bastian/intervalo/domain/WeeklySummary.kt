package dev.bastian.intervalo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayTotal(val date: LocalDate, val seconds: Int, val sessions: Int)

/** The last [DAYS] days ending today, oldest first, with the training done on each. */
data class WeeklySummary(val days: List<DayTotal>) {
    val totalSeconds: Int get() = days.sumOf { it.seconds }
    val totalSessions: Int get() = days.sumOf { it.sessions }

    companion object {
        const val DAYS = 7
    }
}

/** Groups sessions by the local day they started on. Sessions older than the window are ignored. */
fun weeklySummary(sessions: List<Session>, nowMillis: Long, zone: ZoneId): WeeklySummary {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val byDay = sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
    val days = (WeeklySummary.DAYS - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val ofDay = byDay[date].orEmpty()
        DayTotal(date, ofDay.sumOf { it.durationSeconds }, ofDay.size)
    }
    return WeeklySummary(days)
}
