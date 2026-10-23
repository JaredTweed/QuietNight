package com.granularvolume.sleep

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SleepScheduleTest {
    private val zone = ZoneId.of("America/Winnipeg")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant()
    private val night = SleepConfig(mode = ScheduleMode.CUSTOM, start = 22 * 60, end = 7 * 60)
    @Test fun scheduleIsPureAndActivationIsDecidedByTheScheduler() {
        assertTrue(SleepSchedule.plan(night, at("2026-10-23T23:00"), zone).active)
        assertTrue(SleepSchedule.plan(night.copy(screen = false, audio = false), at("2026-10-23T23:00"), zone).active)
    }
    @Test fun alwaysDoesNotDependOnClockOrLocation() {
        assertEquals(SleepPlan(true, null), SleepSchedule.plan(SleepConfig(), at("2026-10-23T14:00"), zone))
    }
    @Test fun overnightIncludesEveningAndFollowingMorning() {
        assertTrue(SleepSchedule.plan(night, at("2026-10-23T23:00"), zone).active)
        assertTrue(SleepSchedule.plan(night, at("2026-10-24T06:59"), zone).active)
        assertFalse(SleepSchedule.plan(night, at("2026-10-24T07:00"), zone).active)
    }
    @Test fun boundariesAreStartInclusiveEndExclusiveAndFutureOnly() {
        val plan = SleepSchedule.plan(night, at("2026-10-23T22:00"), zone)
        assertTrue(plan.active)
        assertEquals(at("2026-10-24T07:00"), plan.nextChange)
        assertEquals(at("2026-10-23T22:00"), SleepSchedule.plan(night, at("2026-10-23T12:00"), zone).nextChange)
    }
    @Test fun daytimeAndZeroLengthSchedulesAreHandled() {
        val day = night.copy(start = 9 * 60, end = 17 * 60)
        assertTrue(SleepSchedule.plan(day, at("2026-10-23T12:00"), zone).active)
        assertFalse(SleepSchedule.plan(day, at("2026-10-23T23:00"), zone).active)
        val invalid = SleepSchedule.plan(day.copy(end = day.start), at("2026-10-23T12:00"), zone)
        assertFalse(invalid.active); assertNotNull(invalid.message)
    }
    @Test fun daylightSavingTransitionKeepsLocalWakeTime() {
        val spring = SleepSchedule.plan(night, at("2026-03-07T23:00"), zone)
        assertEquals(at("2026-03-08T07:00"), spring.nextChange)
        val fall = SleepSchedule.plan(night, at("2026-10-31T23:00"), zone)
        assertEquals(at("2026-11-01T07:00"), fall.nextChange)
    }
    @Test fun solarRequiresLocationAndDoesNotInventTimes() {
        val solar = night.copy(mode = ScheduleMode.SUN)
        val missing = SleepSchedule.plan(solar, at("2026-10-23T23:00"), zone)
        assertFalse(missing.active); assertNotNull(missing.message)
        val polar = SleepSchedule.plan(solar.copy(latitude = 89.0, longitude = 0.0), Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC)
        assertFalse(polar.active); assertNotNull(polar.message); assertNotNull(polar.nextChange)
    }
    @Test fun solarWindowWrapsAcrossDates() {
        val config = night.copy(mode = ScheduleMode.SUN, latitude = 49.9, longitude = -97.14)
        assertTrue(SleepSchedule.plan(config, at("2026-10-23T23:00"), zone).active)
        assertTrue(SleepSchedule.plan(config, at("2026-10-24T04:00"), zone).active)
        assertFalse(SleepSchedule.plan(config, at("2026-10-24T13:00"), zone).active)
    }
    @Test fun equinoxAtEquatorIsNearSixAndEighteen() {
        val date = LocalDate.of(2026, 3, 20)
        val rise = SunTimes.event(date, ZoneOffset.UTC, 0.0, 0.0, true)!!
        val set = SunTimes.event(date, ZoneOffset.UTC, 0.0, 0.0, false)!!
        assertTrue(kotlin.math.abs(Duration.between(date.atTime(6, 0).toInstant(ZoneOffset.UTC), rise).toMinutes()) < 20)
        assertTrue(kotlin.math.abs(Duration.between(date.atTime(18, 0).toInstant(ZoneOffset.UTC), set).toMinutes()) < 20)
    }
    @Test fun solarUtcWrappingStillUsesRequestedLocalDate() {
        for ((name, lat, lon) in listOf(Triple("Pacific/Auckland", -36.85, 174.76), Triple("America/Los_Angeles", 34.05, -118.24))) {
            val z = ZoneId.of(name); val date = LocalDate.of(2026, 10, 23)
            assertEquals(date, SunTimes.event(date, z, lat, lon, true)!!.atZone(z).toLocalDate())
            assertEquals(date, SunTimes.event(date, z, lat, lon, false)!!.atZone(z).toLocalDate())
        }
        assertNull(SunTimes.event(LocalDate.now(), zone, Double.NaN, 0.0, true))
    }
}
