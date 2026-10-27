package dev.jaredtweed.quietnight.sleep

import java.time.*

data class SleepPlan(val active: Boolean, val nextChange: Instant?, val message: String? = null,
                     val activeSince: Instant? = null,
                     val windowStart: Instant? = null, val windowEnd: Instant? = null)

object SleepSchedule {
    fun plan(config: SleepConfig, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): SleepPlan {
        if (config.mode == ScheduleMode.ALWAYS) return SleepPlan(true, null)
        val date = now.atZone(zone).toLocalDate()
        val reference = if (config.mode == ScheduleMode.SUN && config.sunLocationSource == SunLocationSource.TIME_ZONE)
            TimeZoneCities.find(zone) else null
        val latitude = if (config.sunLocationSource == SunLocationSource.TIME_ZONE) reference?.latitude else config.latitude
        val longitude = if (config.sunLocationSource == SunLocationSource.TIME_ZONE) reference?.longitude else config.longitude
        if (config.mode == ScheduleMode.SUN && (latitude == null || longitude == null)) {
            return SleepPlan(false, null, if (config.sunLocationSource == SunLocationSource.TIME_ZONE)
                "No reference city for this time zone. Use a saved location or Custom."
                else "Choose a location for sunset and sunrise")
        }
        if (config.mode == ScheduleMode.CUSTOM && config.start == config.end) {
            return SleepPlan(false, null, "Choose different start and end times")
        }
        val windows = (-2L..3L).mapNotNull { offset ->
            val d = date.plusDays(offset)
            val start: Instant
            val end: Instant
            if (config.mode == ScheduleMode.CUSTOM) {
                start = d.atTime(config.start / 60, config.start % 60).atZone(zone).toInstant()
                val endDate = if (config.end <= config.start) d.plusDays(1) else d
                end = endDate.atTime(config.end / 60, config.end % 60).atZone(zone).toInstant()
            } else {
                start = SunTimes.event(d, zone, latitude!!, longitude!!, false) ?: return@mapNotNull null
                end = SunTimes.event(d.plusDays(1), zone, latitude, longitude, true) ?: return@mapNotNull null
            }
            if (end > start) start to end else null
        }
        val activeWindow = windows.firstOrNull { now >= it.first && now < it.second }
        val displayedWindow = activeWindow ?: windows.firstOrNull { it.first > now }
        val next = windows.flatMap { listOf(it.first, it.second) }.filter { it > now }.minOrNull()
        // Polar day/night: never invent a sunset; check again tomorrow and explain the pause.
        return SleepPlan(activeWindow != null, next ?: date.plusDays(1).atTime(0, 5).atZone(zone).toInstant(),
            if (windows.isEmpty()) "No sunrise or sunset nearby today; use a custom schedule"
            else reference?.let { "Approximate · ${it.name} time zone" }, activeWindow?.first,
            displayedWindow?.first, displayedWindow?.second)
    }
}
