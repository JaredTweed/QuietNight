package dev.jaredtweed.quietnight.sleep

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class TimeZoneCitiesTest {
    @Test fun winnipegHasAnOfflineReferenceCityAndLegacyAliasesResolve() {
        val city = TimeZoneCities.find(ZoneId.of("America/Winnipeg"))!!
        assertEquals("Winnipeg", city.name)
        assertEquals(49.88333333, city.latitude, .000001)
        assertEquals(-97.15, city.longitude, .000001)
        assertEquals(city, TimeZoneCities.find(ZoneId.of("Canada/Central")))
    }
    @Test fun namedCitiesKeepTheirOwnCoordinatesWhenTheirClockRulesAreAliases() {
        val amsterdam = TimeZoneCities.find(ZoneId.of("Europe/Amsterdam"))!!
        assertEquals("Amsterdam", amsterdam.name)
        assertTrue(amsterdam.latitude > 52.0)
        assertNotEquals(amsterdam, TimeZoneCities.find(ZoneId.of("Europe/Brussels")))
    }
    @Test fun fixedOffsetsCannotInventAGeographicalLocation() {
        assertNull(TimeZoneCities.find(ZoneOffset.UTC))
        assertNull(TimeZoneCities.find(ZoneId.of("Etc/GMT+6")))
    }
    @Test fun defaultSunsetScheduleUsesTimeZoneWithoutSavedOrDeviceCoordinates() {
        val config = SleepConfig(mode = ScheduleMode.SUN)
        val zone = ZoneId.of("America/Winnipeg")
        val now = Instant.parse("2026-10-26T05:00:00Z")
        val automatic = SleepSchedule.plan(config, now, zone)
        assertTrue(automatic.active)
        assertNotNull(automatic.nextChange)
        assertEquals("Approximate · Winnipeg time zone", automatic.message)
        val city = TimeZoneCities.find(zone)!!
        val explicit = SleepSchedule.plan(config.copy(sunLocationSource = SunLocationSource.SAVED_LOCATION,
            latitude = city.latitude, longitude = city.longitude), now, zone)
        assertEquals(explicit.active, automatic.active)
        assertEquals(explicit.nextChange, automatic.nextChange)
    }
    @Test fun optionalLocationOverridesEstimateOnlyWhenSelectedAndIsNotDiscarded() {
        val zone = ZoneId.of("America/Winnipeg")
        val now = Instant.parse("2026-06-22T03:00:00Z")
        val config = SleepConfig(mode = ScheduleMode.SUN, latitude = 89.0, longitude = 0.0)
        val automatic = SleepSchedule.plan(config, now, zone)
        val saved = SleepSchedule.plan(config.copy(sunLocationSource = SunLocationSource.SAVED_LOCATION), now, zone)
        assertNotEquals(automatic.nextChange, saved.nextChange)
        assertEquals("No sunrise or sunset nearby today; use a custom schedule", saved.message)
        assertEquals(89.0, config.latitude!!, 0.0)
    }
    @Test fun dateDstAndTimeZoneChangesRecalculateAgainstTheNewReferenceCity() {
        val config = SleepConfig(mode = ScheduleMode.SUN)
        val cache = SleepPlanCache()
        val now = Instant.parse("2026-11-01T07:00:00Z")
        val winnipeg = cache.plan(config, now, ZoneId.of("America/Winnipeg"))
        val sydney = cache.plan(config, now, ZoneId.of("Australia/Sydney"))
        assertNotEquals(winnipeg.nextChange, sydney.nextChange)
        assertEquals("Approximate · Sydney time zone", sydney.message)
        assertEquals(SleepSchedule.plan(config, now.plusSeconds(86400), ZoneId.of("Australia/Sydney")),
            cache.plan(config, now.plusSeconds(86400), ZoneId.of("Australia/Sydney")))
    }
    @Test fun unknownTimeZoneDoesNotSilentlyPretendToKnowSunset() {
        val plan = SleepSchedule.plan(SleepConfig(mode = ScheduleMode.SUN),
            Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC)
        assertFalse(plan.active)
        assertNull(plan.nextChange)
        assertTrue(plan.message!!.contains("No reference city"))
    }
}
