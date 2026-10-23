package com.granularvolume.sleep

import android.content.Context
import androidx.core.content.edit

enum class ScheduleMode { ALWAYS, SUN, CUSTOM }

data class SleepConfig(
    val enabled: Boolean = false,
    val warmth: Int = 45,
    val dim: Int = 25,
    val mode: ScheduleMode = ScheduleMode.ALWAYS,
    val start: Int = 22 * 60,
    val end: Int = 7 * 60,
    val quiet: Boolean = false,
    val volumeDown: Boolean = false,
    val bubble: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null
)

object SleepSettings {
    fun prefs(context: Context) = context.getSharedPreferences("quiet_night", Context.MODE_PRIVATE)
    fun read(context: Context): SleepConfig {
        val p = prefs(context)
        return SleepConfig(
            enabled = p.getBoolean("enabled", false),
            warmth = p.getInt("warmth", 45).coerceIn(0, 100),
            dim = p.getInt("dim", 25).coerceIn(0, 85),
            mode = runCatching { ScheduleMode.valueOf(p.getString("mode", "ALWAYS")!!) }.getOrDefault(ScheduleMode.ALWAYS),
            start = p.getInt("start", 1320).coerceIn(0, 1439),
            end = p.getInt("end", 420).coerceIn(0, 1439),
            quiet = p.getBoolean("quiet", false),
            volumeDown = p.getBoolean("volume_down", false),
            bubble = p.getBoolean("bubble", false),
            latitude = p.getString("latitude", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 },
            longitude = p.getString("longitude", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
        )
    }
    fun write(context: Context, value: SleepConfig) = prefs(context).edit {
        putBoolean("enabled", value.enabled)
        putInt("warmth", value.warmth.coerceIn(0, 100)); putInt("dim", value.dim.coerceIn(0, 85))
        putString("mode", value.mode.name); putInt("start", value.start); putInt("end", value.end)
        putBoolean("quiet", value.quiet); putBoolean("volume_down", value.volumeDown)
        putBoolean("bubble", value.bubble)
        putString("latitude", value.latitude?.toString()); putString("longitude", value.longitude?.toString())
    }
    fun update(context: Context, change: (SleepConfig) -> SleepConfig) = write(context, change(read(context)))
}
