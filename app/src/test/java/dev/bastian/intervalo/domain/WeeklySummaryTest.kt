package dev.bastian.intervalo.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklySummaryTest {

    private val zone = ZoneId.of("Europe/Madrid")

    private fun millis(date: String, time: String = "12:00") =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun session(date: String, seconds: Int, time: String = "12:00", completed: Boolean = true) = Session(
        routineId = 1,
        routineName = "R",
        startedAt = millis(date, time),
        durationSeconds = seconds,
        completed = completed,
    )

    private val now = millis("2026-11-10", "20:00")

    @Test
    fun coversTheLastSevenDaysEndingTodayOldestFirst() {
        val summary = weeklySummary(emptyList(), now, zone)

        assertEquals(7, summary.days.size)
        assertEquals(LocalDate.parse("2026-11-04"), summary.days.first().date)
        assertEquals(LocalDate.parse("2026-11-10"), summary.days.last().date)
        assertEquals(0, summary.totalSessions)
    }

    @Test
    fun sumsSessionsPerLocalDay() {
        val summary = weeklySummary(
            listOf(
                session("2026-11-10", 600),
                session("2026-11-10", 300, completed = false),
                session("2026-11-08", 1200),
            ),
            now,
            zone,
        )

        assertEquals(900, summary.days.last().seconds)
        assertEquals(2, summary.days.last().sessions)
        assertEquals(1200, summary.days[4].seconds)
        assertEquals(2100, summary.totalSeconds)
        assertEquals(3, summary.totalSessions)
    }

    @Test
    fun ignoresSessionsOutsideTheWindow() {
        val summary = weeklySummary(
            listOf(session("2026-11-03", 999), session("2026-11-04", 60)),
            now,
            zone,
        )

        assertEquals(60, summary.totalSeconds)
        assertEquals(1, summary.totalSessions)
    }

    @Test
    fun aLateNightSessionBelongsToTheDayItStartedOn() {
        val summary = weeklySummary(listOf(session("2026-11-09", 600, time = "23:50")), now, zone)

        assertEquals(600, summary.days[5].seconds)
        assertEquals(0, summary.days.last().seconds)
    }
}
