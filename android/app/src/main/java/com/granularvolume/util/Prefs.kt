package com.granularvolume.util

import android.content.Context
import androidx.core.content.edit

/** Audio-engine settings, retaining the existing calibration keys across fork updates. */
object Prefs {
    private const val KEY_BLUETOOTH_FLOOR_PREFIX = "bluetooth_floor_"
    private const val KEY_HAS_BLUETOOTH_CALIBRATION = "has_bluetooth_calibration"
    const val ATTENUATION_DEFAULT = 0f
    const val ATTENUATION_MIN = -30f
    const val ATTENUATION_MAX = 0f
    private fun prefs(context: Context) = context.getSharedPreferences("gv_prefs", Context.MODE_PRIVATE)
    fun getAttenuation(context: Context) = prefs(context).getFloat("attenuation_db", ATTENUATION_DEFAULT)
    fun setAttenuation(context: Context, dB: Float) {
        prefs(context).edit { putFloat("attenuation_db", dB.coerceIn(ATTENUATION_MIN, ATTENUATION_MAX)) }
    }
    fun getBluetoothFloor(context: Context, deviceName: String) =
        prefs(context).getInt(KEY_BLUETOOTH_FLOOR_PREFIX + deviceName, 1)
    @Synchronized fun hasBluetoothCalibration(context: Context): Boolean {
        val p = prefs(context)
        if (p.contains(KEY_HAS_BLUETOOTH_CALIBRATION)) return p.getBoolean(KEY_HAS_BLUETOOTH_CALIBRATION, false)
        val exists = p.all.keys.any { it.startsWith(KEY_BLUETOOTH_FLOOR_PREFIX) }
        p.edit { putBoolean(KEY_HAS_BLUETOOTH_CALIBRATION, exists) }
        return exists
    }
    @Synchronized fun setBluetoothFloor(context: Context, deviceName: String, index: Int) {
        prefs(context).edit {
            putInt(KEY_BLUETOOTH_FLOOR_PREFIX + deviceName, index)
            putBoolean(KEY_HAS_BLUETOOTH_CALIBRATION, true)
        }
    }
    fun hasLevelZone(context: Context) = prefs(context).contains("level_in_quiet_zone")
    fun isLevelInQuietZone(context: Context) = prefs(context).getBoolean("level_in_quiet_zone", false)
    fun setLevelInQuietZone(context: Context, quiet: Boolean) {
        prefs(context).edit { putBoolean("level_in_quiet_zone", quiet) }
    }
}
