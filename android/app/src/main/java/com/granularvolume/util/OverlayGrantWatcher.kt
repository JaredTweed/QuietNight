package com.granularvolume.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.granularvolume.MainActivity

/**
 * Brings the user back from Android's "Display over other apps" screen the moment the
 * switch is turned on (1.7.2).
 *
 * Why it exists: since Android 11 the system opens the list of every app instead of this
 * app's own switch, and after the switch there is nothing on that screen that says "go
 * back". On most devices it takes two back presses. People turned the switch on and stayed
 * there, or left. The app is allowed to watch its OWN permission, so it does, and returns
 * the user by itself; "then press back" stops being a step anyone has to work out.
 *
 * It watches only while the user is on that errand: it starts when the settings screen is
 * opened from the main screen, stops when the main screen is in front again, and gives up
 * after [WINDOW_MS], so someone who turns the switch on an hour later from Settings is not
 * pulled out of whatever they are doing.
 */
object OverlayGrantWatcher {

    private const val TAG = "GranularVolume:Grant"

    /** How long after the settings screen was opened a grant still returns the user. */
    private const val WINDOW_MS = 5 * 60 * 1000L

    /** The op callback can arrive a moment before canDrawOverlays() reports it. */
    private const val SETTLE_MS = 150L
    private const val RECHECK_MS = 200L
    private const val RECHECKS = 5

    private val main = Handler(Looper.getMainLooper())
    private var listener: AppOpsManager.OnOpChangedListener? = null
    private var startedAt = 0L

    /** True while a grant would bring the user back. Read by the main screen and the tests. */
    val watching: Boolean get() = listener != null

    fun start(context: Context) {
        val app = context.applicationContext
        stop(app)
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return
        val l = AppOpsManager.OnOpChangedListener { _, pkg ->
            if (pkg != app.packageName) return@OnOpChangedListener
            main.postDelayed({ check(app, 0) }, SETTLE_MS)
        }
        val ok = runCatching {
            ops.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.packageName, l)
        }.onFailure { Log.w(TAG, "cannot watch the overlay switch: ${it.message}") }.isSuccess
        if (!ok) return
        listener = l
        startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "watching the overlay switch")
    }

    private fun check(app: Context, attempt: Int) {
        if (listener == null) return
        if (SystemClock.elapsedRealtime() - startedAt > WINDOW_MS) {
            Log.i(TAG, "grant window passed, not returning")
            stop(app)
            return
        }
        if (!Settings.canDrawOverlays(app)) {
            if (attempt < RECHECKS) main.postDelayed({ check(app, attempt + 1) }, RECHECK_MS)
            return
        }
        stop(app)
        // An app that may draw over other apps may also bring its own screen forward, so
        // this start is allowed from here. If a device refuses it anyway, nothing is lost:
        // the user presses back as before and the main screen carries on from there.
        runCatching {
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            Log.i(TAG, "switch turned on, returning to the app")
        }.onFailure { Log.w(TAG, "return to the app refused: ${it.message}") }
    }

    fun stop(context: Context) {
        val l = listener ?: return
        listener = null
        main.removeCallbacksAndMessages(null)
        val ops = context.applicationContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        runCatching { ops?.stopWatchingMode(l) }
        Log.i(TAG, "stopped watching the overlay switch")
    }
}
