package com.granularvolume.sleep

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import kotlin.math.roundToInt

/** Public settings API: this reaches system UI too, unlike an application overlay. */
object ScreenDimming {
    enum class Mode { OFF, SYSTEM, APP_ONLY, USER_CONTROL, RESTORE_NEEDED }
    data class Result(val mode: Mode, val overlayDim: Int)
    private fun session(context: Context) = BrightnessSession(
        AndroidBrightness(context), BrightnessPreferences(context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE)))
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)
    fun permissionIntent(context: Context) = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
    @Synchronized fun apply(context: Context, percent: Int): Result = session(context).apply(percent)
    @Synchronized fun restore(context: Context): Boolean = session(context).restore()
    @Synchronized fun checkForUserChange(context: Context): Boolean = session(context).checkForUserChange()
    fun userHasControl(context: Context) = context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE).getBoolean("yielded", false)
    fun needsRestore(context: Context) = context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE).contains("original")
    @Synchronized fun resume(context: Context) {
        val p = context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE)
        p.edit().remove("yielded").commit()
    }
}

/** Small, testable transaction around a setting that survives process death. */
internal data class BrightnessRecord(val original: Int, val originalMode: Int, val target: Int, val phase: String,
                                     val previous: Int = original)
internal interface BrightnessAccess {
    fun permitted(): Boolean
    fun brightness(): Int?
    fun mode(): Int?
    fun setBrightness(value: Int): Boolean
    fun setMode(value: Int): Boolean
}
internal interface BrightnessStorage {
    fun record(): BrightnessRecord?
    fun yielded(): Boolean
    /** Synchronous persistence must succeed BEFORE global settings are changed. */
    fun save(record: BrightnessRecord?, yielded: Boolean = false): Boolean
}

internal class BrightnessSession(private val access: BrightnessAccess, private val storage: BrightnessStorage) {
    private val manual = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    private fun currentMatches(record: BrightnessRecord) =
        access.brightness() == record.target && access.mode() == manual
    private fun inTransaction(record: BrightnessRecord): Boolean =
        access.brightness() in listOf(record.original, record.previous, record.target) &&
            access.mode() in listOf(record.originalMode, manual)
    fun checkForUserChange(): Boolean {
        val record = storage.record() ?: return storage.yielded()
        if (access.brightness() == null || access.mode() == null) return storage.yielded()
        if ((record.phase == "active" && !currentMatches(record)) ||
            (record.phase != "active" && !inTransaction(record))) {
            // A Quick Settings/Settings change wins. Never fight the user or overwrite it on stop.
            storage.save(null, yielded = true)
            return true
        }
        return storage.yielded()
    }
    fun apply(value: Int): ScreenDimming.Result {
        val percent = value.coerceIn(0, 85)
        if (percent == 0) {
            val restored = restore()
            return ScreenDimming.Result(if (restored) ScreenDimming.Mode.OFF else ScreenDimming.Mode.RESTORE_NEEDED, 0)
        }
        if (checkForUserChange()) return ScreenDimming.Result(ScreenDimming.Mode.USER_CONTROL, 0)
        var record = storage.record()
        if (record != null && (access.brightness() == null || access.mode() == null))
            return ScreenDimming.Result(ScreenDimming.Mode.RESTORE_NEEDED, 0)
        if (!access.permitted()) return ScreenDimming.Result(
            if (record != null) ScreenDimming.Mode.RESTORE_NEEDED else ScreenDimming.Mode.APP_ONLY,
            if (record != null && access.brightness() in listOf(record.target, record.previous) &&
                access.brightness() != record.original) 0 else percent)
        if (record?.phase == "restoring") {
            if (!restore()) return ScreenDimming.Result(ScreenDimming.Mode.RESTORE_NEEDED, 0)
            record = null
        }
        if (record != null && record.phase == "applying" && !inTransaction(record)) {
            storage.save(null, yielded = true)
            return ScreenDimming.Result(ScreenDimming.Mode.USER_CONTROL, 0)
        }
        val original = record?.original ?: access.brightness()
        val originalMode = record?.originalMode ?: access.mode()
        if (original == null || original !in 0..255 || originalMode !in 0..1)
            return ScreenDimming.Result(ScreenDimming.Mode.APP_ONLY, percent)
        // Always use the ORIGINAL baseline. Slider changes and service restarts cannot compound.
        val target = (original * (1 - percent / 100f)).roundToInt().coerceIn(if (original == 0) 0 else 1, 255)
        if (record?.phase == "active" && record.target == target && currentMatches(record))
            return ScreenDimming.Result(ScreenDimming.Mode.SYSTEM, 0)
        val next = BrightnessRecord(original, originalMode!!, target, "applying", access.brightness() ?: original)
        if (!storage.save(next)) return ScreenDimming.Result(ScreenDimming.Mode.APP_ONLY, percent)
        if ((access.mode() != manual && !access.setMode(manual)) || !access.setBrightness(target)) {
            val restored = restore()
            return ScreenDimming.Result(if (restored) ScreenDimming.Mode.APP_ONLY else ScreenDimming.Mode.RESTORE_NEEDED,
                if (restored) percent else 0)
        }
        storage.save(next.copy(phase = "active"))
        return ScreenDimming.Result(ScreenDimming.Mode.SYSTEM, 0)
    }
    fun restore(): Boolean {
        val record = storage.record()
        if (record == null) return if (storage.yielded()) storage.save(null) else true
        if (access.brightness() == null || access.mode() == null) return false
        // An interrupted apply/restore is recoverable, but a genuinely different user setting wins.
        if ((record.phase == "active" && !currentMatches(record)) ||
            (record.phase != "active" && !inTransaction(record))) return storage.save(null)
        if (!access.permitted() || !storage.save(record.copy(phase = "restoring"))) return false
        val brightnessRestored = access.brightness() == record.original || access.setBrightness(record.original)
        val modeRestored = access.mode() == record.originalMode || access.setMode(record.originalMode)
        if (!brightnessRestored || !modeRestored) return false
        return storage.save(null)
    }
}

private class BrightnessPreferences(private val prefs: SharedPreferences) : BrightnessStorage {
    override fun yielded() = prefs.getBoolean("yielded", false)
    override fun record(): BrightnessRecord? = if (!prefs.contains("original")) null else BrightnessRecord(
        prefs.getInt("original", -1), prefs.getInt("mode", 0), prefs.getInt("target", -1), prefs.getString("phase", "active")!!,
        prefs.getInt("previous", prefs.getInt("original", -1)))
    override fun save(record: BrightnessRecord?, yielded: Boolean): Boolean = prefs.edit().clear().apply {
        putBoolean("yielded", yielded)
        if (record != null) {
            putInt("original", record.original); putInt("mode", record.originalMode)
            putInt("target", record.target); putString("phase", record.phase)
            putInt("previous", record.previous)
        }
    }.commit()
}

private class AndroidBrightness(context: Context) : BrightnessAccess {
    private val app = context.applicationContext
    override fun permitted() = ScreenDimming.canWrite(app)
    override fun brightness(): Int? = runCatching { Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
    override fun mode(): Int? = runCatching { Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) }.getOrNull()
    override fun setBrightness(value: Int) = runCatching { Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value) }.getOrDefault(false)
    override fun setMode(value: Int) = runCatching { Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, value) }.getOrDefault(false)
}
