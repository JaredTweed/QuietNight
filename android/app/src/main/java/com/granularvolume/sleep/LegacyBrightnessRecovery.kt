package com.granularvolume.sleep

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Retires brightness transactions created by pre-overlay versions; never starts a new one. */
object LegacyBrightnessRecovery {
    private fun prefs(context: Context) = context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE)
    fun pending(context: Context): Boolean = prefs(context).contains("original")
    fun permissionIntent(context: Context) = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}"))

    @Synchronized fun restore(context: Context): Boolean {
        val p = prefs(context)
        if (!pending(context)) return true
        val original = p.getInt("original", -1)
        val mode = p.getInt("mode", -1)
        val target = p.getInt("target", -1)
        val previous = p.getInt("previous", original)
        val phase = p.getString("phase", "active")
        if (original !in 0..255 || mode !in 0..1 || target !in 0..255)
            return p.edit().clear().commit()
        val resolver = context.contentResolver
        val brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        val currentMode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        if (brightness < 0 || currentMode < 0) return false
        val stillOwned = if (phase == "active") brightness == target && currentMode == 0 else
            brightness in listOf(original, previous, target) && currentMode in listOf(0, mode)
        // A manual change after the old session wins, including during an interrupted restore.
        if (!stillOwned || (brightness == original && currentMode == mode)) return p.edit().clear().commit()
        if (!Settings.System.canWrite(context) || !p.edit().putString("phase", "restoring").commit()) return false
        return try {
            val restoredBrightness = brightness == original ||
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, original)
            val restoredMode = currentMode == mode ||
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            restoredBrightness && restoredMode && p.edit().clear().commit()
        } catch (_: SecurityException) { false }
    }
}
