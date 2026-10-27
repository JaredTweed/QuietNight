package dev.jaredtweed.quietnight.sleep

import android.content.Context
import androidx.core.content.edit

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
            latitude = p.getString("latitude", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 },
            longitude = p.getString("longitude", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 },
            sunLocationSource = runCatching { SunLocationSource.valueOf(p.getString("sun_source", "TIME_ZONE")!!) }
                .getOrDefault(SunLocationSource.TIME_ZONE),
            fadeInMinutes = p.getInt("fade_in_minutes", 0).coerceIn(0, SleepConfig.MAX_FADE_IN_MINUTES)
        )
    }
    fun write(context: Context, value: SleepConfig) = prefs(context).edit {
        putBoolean("screen_enabled", value.screen)
        putBoolean("audio_enabled", value.audio)
        remove("enabled"); remove("quiet")
        putInt("warmth", value.warmth.coerceIn(0, 100)); putInt("dim", value.dim.coerceIn(0, 85))
        putString("mode", value.mode.name); putInt("start", value.start); putInt("end", value.end)
        remove("volume_down"); remove("bubble")
        putString("latitude", value.latitude?.toString()); putString("longitude", value.longitude?.toString())
        putString("sun_source", value.sunLocationSource.name)
        putInt("fade_in_minutes", value.fadeInMinutes.coerceIn(0, SleepConfig.MAX_FADE_IN_MINUTES))
    }
    fun update(context: Context, change: (SleepConfig) -> SleepConfig) = write(context, change(read(context)))
}
