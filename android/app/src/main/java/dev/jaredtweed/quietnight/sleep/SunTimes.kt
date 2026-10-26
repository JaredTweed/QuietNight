package dev.jaredtweed.quietnight.sleep

import java.time.*
import kotlin.math.*

/** Offline sunrise/sunset approximation, using a 90.833° solar zenith (standard refraction). */
object SunTimes {
    fun event(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double, sunrise: Boolean): Instant? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        fun sinD(x: Double) = sin(Math.toRadians(x))
        fun cosD(x: Double) = cos(Math.toRadians(x))
        fun wrap(x: Double, range: Double) = ((x % range) + range) % range
        val longitudeHour = longitude / 15.0
        val approximateDay = date.dayOfYear + ((if (sunrise) 6.0 else 18.0) - longitudeHour) / 24.0
        val meanAnomaly = 0.9856 * approximateDay - 3.289
        val solarLongitude = wrap(meanAnomaly + 1.916 * sinD(meanAnomaly) + 0.020 * sinD(2 * meanAnomaly) + 282.634, 360.0)
        var rightAscension = wrap(Math.toDegrees(atan(0.91764 * tan(Math.toRadians(solarLongitude)))), 360.0)
        rightAscension += floor(solarLongitude / 90) * 90 - floor(rightAscension / 90) * 90
        rightAscension /= 15
        val sineDeclination = 0.39782 * sinD(solarLongitude)
        val cosineDeclination = cos(asin(sineDeclination))
        val cosineHourAngle = (cosD(90.833) - sineDeclination * sinD(latitude)) / (cosineDeclination * cosD(latitude))
        if (!cosineHourAngle.isFinite() || cosineHourAngle !in -1.0..1.0) return null
        val hourAngleHours = (if (sunrise) 360 - Math.toDegrees(acos(cosineHourAngle)) else Math.toDegrees(acos(cosineHourAngle))) / 15
        val utcHours = wrap(hourAngleHours + rightAscension - 0.06571 * approximateDay - 6.622 - longitudeHour, 24.0)
        var result = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds((utcHours * 3600).roundToLong())
        // The formula wraps UTC hours. Match the requested LOCAL date at either side of midnight.
        if (result.atZone(zone).toLocalDate() < date) result = result.plusSeconds(86400)
        if (result.atZone(zone).toLocalDate() > date) result = result.minusSeconds(86400)
        return result
    }
}
