package dev.jaredtweed.quietnight.sleep

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.os.Build
import kotlin.math.roundToInt
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Touch-through filter. Trusted accessibility windows also cover notifications and Quick Settings. */
class NightOverlay(private val context: Context, private val systemUi: Boolean = false) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var filter: View? = null
    private data class Appearance(val dim: Int, val warmth: Int)
    private var applied: Appearance? = null
    private val canOverlay: Boolean get() = systemUi || Settings.canDrawOverlays(context)

    fun update(config: SleepConfig): Boolean {
        if (!canOverlay) {
            removeFilter(); return false
        }
        val wantsFilter = config.screen && (config.dim > 0 || config.warmth > 0)
        if (!wantsFilter) { removeFilter(); return true } else {
            val appearance = Appearance(config.dim, config.warmth)
            if (applied == appearance) return true
            val view = filter ?: View(context).also { filter = it }
            view.setBackgroundColor(if (systemUi) filterColor(config.dim, config.warmth)
                else if (config.warmth == 0) Color.TRANSPARENT else Color.rgb(255, 67, 15))
            val lp = WindowManager.LayoutParams(-1, -1, if (systemUi) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    (if (systemUi) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else WindowManager.LayoutParams.FLAG_DIM_BEHIND),
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                dimAmount = if (systemUi) 0f else config.dim.coerceIn(0, 85) / 100f
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
                // Keep the tint itself translucent so ordinary app touches pass through.
                // Android's dim layer provides the deeper dimming without blocking input.
                alpha = if (systemUi) 1f else if (config.warmth == 0) 0.01f else
                    (config.warmth / 100f * 0.55f).coerceAtMost(0.55f)
                title = "Quiet Night screen filter"
            }
            try {
                if (applied == null) wm.addView(view, lp) else wm.updateViewLayout(view, lp)
                applied = appearance
                return true
            } catch (_: SecurityException) {
                // Access can be revoked between the permission check and the window operation.
                removeFilter()
                return false
            } catch (_: WindowManager.BadTokenException) {
                // A disconnect invalidates the accessibility window token.
                removeFilter()
                return false
            } catch (_: IllegalArgumentException) {
                // WindowManager may have removed the view during a display/service transition.
                removeFilter()
                return false
            }
        }
    }

    private fun removeFilter() {
        filter?.let { runCatching { wm.removeView(it) } }; filter = null; applied = null
    }
    fun close() { removeFilter() }

    companion object {
        /** Composite the tint under black dimming so dimming cannot brighten the filter. */
        internal fun filterColor(dim: Int, warmth: Int): Int {
            val d = dim.coerceIn(0, 85) / 100f
            val w = warmth.coerceIn(0, 100) / 100f * .55f
            val opacity = d + w * (1f - d)
            if (opacity == 0f) return Color.TRANSPARENT
            val tint = w * (1f - d) / opacity
            return Color.argb((opacity * 255).roundToInt(), (255 * tint).roundToInt(),
                (67 * tint).roundToInt(), (15 * tint).roundToInt())
        }
    }
}
