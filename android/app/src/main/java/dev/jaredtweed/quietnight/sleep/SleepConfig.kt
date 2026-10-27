package dev.jaredtweed.quietnight.sleep

enum class ScheduleMode { ALWAYS, SUN, CUSTOM }
enum class SunLocationSource { TIME_ZONE, SAVED_LOCATION }

/**
 * Screen and audio are deliberately independent switches. Each bottom-right control
 * can be enabled on its own. The schedule belongs to Screen; Audio stays manual.
 */
data class SleepConfig(
    val screen: Boolean = false,
    val audio: Boolean = false,
    val warmth: Int = 45,
    val dim: Int = 25,
    val mode: ScheduleMode = ScheduleMode.ALWAYS,
    val start: Int = 22 * 60,
    val end: Int = 7 * 60,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sunLocationSource: SunLocationSource = SunLocationSource.TIME_ZONE,
    val fadeInMinutes: Int = 0
) {
    companion object { const val MAX_FADE_IN_MINUTES = 60 }
}
