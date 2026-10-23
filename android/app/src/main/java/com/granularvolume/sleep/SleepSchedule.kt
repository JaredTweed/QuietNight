package com.granularvolume.sleep

import java.time.*
import kotlin.math.*

/** Offline sunrise/sunset approximation, using a 90.833° solar zenith (standard refraction). */
object SunTimes {
    fun event(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double, sunrise: Boolean): Instant? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        fun sinD(x: Double) = sin(Math.toRadians(x))
        fun cosD(x: Double) = cos(Math.toRadians(x))
        fun wrap(x: Double, range: Double) = ((x % range) + range) % range
        val hour = longitude / 15.0
        val t = date.dayOfYear + ((if (sunrise) 6.0 else 18.0) - hour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = wrap(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634, 360.0)
        var ra = wrap(Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l)))), 360.0)
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(90.833) - sinDec * sinD(latitude)) / (cosDec * cosD(latitude))
        if (!cosH.isFinite() || cosH !in -1.0..1.0) return null
        val h = (if (sunrise) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15
        val utc = wrap(h + ra - 0.06571 * t - 6.622 - hour, 24.0)
        var result = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds((utc * 3600).roundToLong())
        // The formula wraps UTC hours. Match the requested LOCAL date at either side of midnight.
        if (result.atZone(zone).toLocalDate() < date) result = result.plusSeconds(86400)
        if (result.atZone(zone).toLocalDate() > date) result = result.minusSeconds(86400)
        return result
    }
}

data class SleepPlan(val active: Boolean, val nextChange: Instant?, val message: String? = null)

object SleepSchedule {
    fun plan(config: SleepConfig, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): SleepPlan {
        if (!config.enabled) return SleepPlan(false, null)
        if (config.mode == ScheduleMode.ALWAYS) return SleepPlan(true, null)
        val date = now.atZone(zone).toLocalDate()
        if (config.mode == ScheduleMode.SUN && (config.latitude == null || config.longitude == null)) {
            return SleepPlan(false, null, "Choose a location for sunset and sunrise")
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
                start = SunTimes.event(d, zone, config.latitude!!, config.longitude!!, false) ?: return@mapNotNull null
                end = SunTimes.event(d.plusDays(1), zone, config.latitude, config.longitude, true) ?: return@mapNotNull null
            }
            if (end > start) start to end else null
        }
        val active = windows.any { now >= it.first && now < it.second }
        val next = windows.flatMap { listOf(it.first, it.second) }.filter { it > now }.minOrNull()
        // Polar day/night: never invent a sunset; check again tomorrow and explain the pause.
        return SleepPlan(active, next ?: date.plusDays(1).atTime(0, 5).atZone(zone).toInstant(),
            if (windows.isEmpty()) "No sunrise or sunset nearby today; use a custom schedule" else null)
    }
}
