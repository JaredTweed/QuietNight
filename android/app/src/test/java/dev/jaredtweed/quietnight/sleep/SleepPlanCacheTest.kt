package dev.jaredtweed.quietnight.sleep

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SleepPlanCacheTest {
    private val zone = ZoneId.of("America/Winnipeg")
    private val now = Instant.parse("2026-10-26T23:00:00Z")
    private val config = SleepConfig(mode = ScheduleMode.CUSTOM)

    @Test fun repeatedRefreshesReuseThePlanUntilTheExactBoundary() {
        var calls = 0
        val cache = SleepPlanCache { _, at, _ -> calls++; SleepPlan(false, at.plusSeconds(60)) }
        val first = cache.plan(config, now, zone)
        repeat(240) { assertSame(first, cache.plan(config, now.plusMillis(it * 250L), zone)) }
        assertEquals(1, calls)
        cache.plan(config, now.plusSeconds(60), zone)
        assertEquals(2, calls)
    }
    @Test fun clockAndZoneAndConfigChangesInvalidateThePlan() {
        var calls = 0
        val cache = SleepPlanCache { _, at, _ -> calls++; SleepPlan(true, at.plusSeconds(3600)) }
        cache.plan(config, now, zone)
        cache.plan(config, now.minusSeconds(1), zone)
        cache.plan(config, now, ZoneId.of("UTC"))
        cache.plan(config.copy(start = 60), now, ZoneId.of("UTC"))
        assertEquals(4, calls)
    }
    @Test fun cachedPlansMatchUncachedPlansAcrossRealBoundariesAndDst() {
        val cache = SleepPlanCache()
        val start = Instant.parse("2026-10-31T20:00:00Z")
        repeat(48 * 12) { n ->
            val at = start.plusSeconds(n * 300L)
            assertEquals(SleepSchedule.plan(config, at, zone), cache.plan(config, at, zone))
        }
    }
}
