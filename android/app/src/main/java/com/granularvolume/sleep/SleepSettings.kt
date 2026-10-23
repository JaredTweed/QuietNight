package com.granularvolume.sleep

import android.content.Context
import androidx.core.content.edit

enum class ScheduleMode { ALWAYS, SUN, CUSTOM }

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
    val volumeDown: Boolean = false,
    val bubble: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null
)

object SleepSettings {
    fun prefs(context: Context) = context.getSharedPreferences("quiet_night", Context.MODE_PRIVATE)
    fun read(context: Context): SleepConfig {
        val p = prefs(context)
        // Migrate the short-lived single "wind-down" switch without losing a user's choices.
        val legacyEnabled = p.getBoolean("enabled", false)
        val screen = if (p.contains("screen_enabled")) p.getBoolean("screen_enabled", false) else legacyEnabled
        val audio = if (p.contains("audio_enabled")) p.getBoolean("audio_enabled", false)
            else legacyEnabled && p.getBoolean("quiet", false)
        return SleepConfig(
            screen = screen,
            audio = audio,
            warmth = p.getInt("warmth", 45).coerceIn(0, 100),
            dim = p.getInt("dim", 25).coerceIn(0, 85),
            mode = runCatching { ScheduleMode.valueOf(p.getString("mode", "ALWAYS")!!) }.getOrDefault(ScheduleMode.ALWAYS),
            start = p.getInt("start", 1320).coerceIn(0, 1439),
            end = p.getInt("end", 420).coerceIn(0, 1439),
            volumeDown = p.getBoolean("volume_down", false),
            bubble = p.getBoolean("bubble", false),
            latitude = p.getString("latitude", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 },
            longitude = p.getString("longitude", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
        )
    }
    fun write(context: Context, value: SleepConfig) = prefs(context).edit {
        putBoolean("screen_enabled", value.screen)
        putBoolean("audio_enabled", value.audio)
        remove("enabled"); remove("quiet")
        putInt("warmth", value.warmth.coerceIn(0, 100)); putInt("dim", value.dim.coerceIn(0, 85))
        putString("mode", value.mode.name); putInt("start", value.start); putInt("end", value.end)
        putBoolean("volume_down", value.volumeDown)
        putBoolean("bubble", value.bubble)
        putString("latitude", value.latitude?.toString()); putString("longitude", value.longitude?.toString())
    }
    fun update(context: Context, change: (SleepConfig) -> SleepConfig) = write(context, change(read(context)))
}
