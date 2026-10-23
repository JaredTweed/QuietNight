package com.granularvolume.sleep

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A touch-through evening tint and dim layer drawn above other apps.
 *
 * Dimming is an overlay rather than a change to the system brightness setting, so the
 * user's saved brightness and adaptive-brightness mode are never touched. The window
 * covers normal app content; Android still keeps a few privileged system surfaces
 * above application overlays.
 */
class NightOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var filter: View? = null
    private val canOverlay: Boolean get() = Settings.canDrawOverlays(context)

    fun update(config: SleepConfig) {
        if (!canOverlay) {
            removeFilter(); return
        }
        val wantsFilter = config.screen && (config.dim > 0 || config.warmth > 0)
        if (!wantsFilter) removeFilter() else {
            val view = filter ?: View(context).also { filter = it }
            view.setBackgroundColor(if (config.warmth == 0) Color.TRANSPARENT else Color.rgb(255, 67, 15))
            val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                dimAmount = config.dim / 100f
                // Keep the tint itself translucent so ordinary app touches pass through.
                // Android's dim layer provides the deeper dimming without blocking input.
                alpha = if (config.warmth == 0) 0.01f else
                    (config.warmth / 100f * 0.55f).coerceAtMost(0.55f)
                title = "Quiet Night screen filter"
            }
            if (view.parent == null) wm.addView(view, lp) else wm.updateViewLayout(view, lp)
        }
    }

    private fun removeFilter() {
        filter?.let { runCatching { wm.removeView(it) } }; filter = null
    }
    fun close() { removeFilter() }
}
