package dev.jaredtweed.quietnight.sleep

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ScheduledFilterFadeTest {
    private val start = Instant.parse("2026-10-27T03:00:00Z")
    private val end = start.plusSeconds(9 * 3600)
    private val plan = SleepPlan(true, end, activeSince = start)
    private val config = SleepConfig(screen = true, mode = ScheduleMode.CUSTOM, fadeInMinutes = 10)

    @Test fun existingSettingsAndManualModeRemainInstant() {
        assertNull(ScheduledFilterFade.from(config.copy(fadeInMinutes = 0), plan))
        assertNull(ScheduledFilterFade.from(config.copy(mode = ScheduleMode.ALWAYS), plan))
        assertNull(ScheduledFilterFade.from(config.copy(screen = false), plan))
        assertNull(ScheduledFilterFade.from(config, plan.copy(active = false)))
        assertNull(ScheduledFilterFade.from(config, null))
    }

    @Test fun phaseFollowsActualWindowStartAndFinishesWithoutFurtherTicks() {
        val fade = ScheduledFilterFade.from(config, plan)!!
        assertEquals(0, fade.opacityStep(start))
        assertEquals(127, fade.opacityStep(start.plusSeconds(300)))
        assertEquals(255, fade.opacityStep(start.plusSeconds(600)))
        assertEquals(255, fade.opacityStep(start.plusSeconds(3600)))
        assertNull(fade.nextUpdateDelay(start.plusSeconds(600)))
        assertEquals(fade, ScheduledFilterFade.from(config.copy(audio = true), plan))
    }

    @Test fun everyTickAdvancesAndLongFadesHaveABoundedNumberOfCallbacks() {
        val fade = ScheduledFilterFade.from(config.copy(fadeInMinutes = 60), plan)!!
        var now = start
        var steps = 0
        while (true) {
            val delay = fade.nextUpdateDelay(now) ?: break
            assertTrue(delay > 0)
            val previous = fade.opacityStep(now)
            now = now.plusMillis(delay)
            assertTrue(fade.opacityStep(now) > previous)
            steps++
            assertTrue(steps <= 255)
        }
        assertEquals(255, steps)
        assertEquals(start.plusSeconds(3600), now)
    }

    @Test fun aShortWindowStillStopsOnTimeAndClockChangesDoNotRestartTheFade() {
        val fade = ScheduledFilterFade(start, start.plusSeconds(1), 600_000)
        assertEquals(1000L, fade.nextUpdateDelay(start))
        assertTrue(fade.finishedWindow(start.plusSeconds(1)))
        assertNull(fade.nextUpdateDelay(start.plusSeconds(1)))
        assertEquals(0, fade.opacityStep(start.minusSeconds(60)))
    }

    @Test fun overnightAndSolarPlansExposeTheirRealStartForRestartsAndWake() {
        val zone = ZoneId.of("America/Winnipeg")
        val now = Instant.parse("2026-10-27T05:00:00Z")
        val overnight = SleepSchedule.plan(config.copy(start = 22 * 60, end = 7 * 60), now, zone)
        assertTrue(overnight.active)
        assertEquals(start, overnight.activeSince)
        val solar = SleepSchedule.plan(config.copy(mode = ScheduleMode.SUN), now, zone)
        assertTrue(solar.active)
        assertEquals(SunTimes.event(java.time.LocalDate.of(2026, 10, 26), zone,
            TimeZoneCities.find(zone)!!.latitude, TimeZoneCities.find(zone)!!.longitude, false), solar.activeSince)
    }
}
