package dev.jaredtweed.quietnight.sleep

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.time.Instant
import kotlin.math.roundToInt

/** Touch-through filter. Trusted accessibility windows also cover notifications and Quick Settings. */
class NightOverlay(
    private val context: Context,
    private val systemUi: Boolean = false,
    private val now: () -> Instant = Instant::now
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var filter: View? = null
    private data class Request(val dim: Int, val warmth: Int, val fade: ScheduledFilterFade?)
    private data class Appearance(val color: Int, val dimAmount: Float, val windowAlpha: Float)
    private var requested: Request? = null
    private var applied: Appearance? = null
    private val advance = Runnable { renderFrame() }
    private val canOverlay: Boolean get() = systemUi || Settings.canDrawOverlays(context)

    fun update(config: SleepConfig, plan: SleepPlan? = null): Boolean {
        if (!canOverlay) { removeFilter(); return false }
        if (!config.screen || (config.dim == 0 && config.warmth == 0)) {
            removeFilter(); return true
        }
        handler.removeCallbacks(advance)
        requested = Request(config.dim.coerceIn(0, 85), config.warmth.coerceIn(0, 100),
            ScheduledFilterFade.from(config, plan))
        // Re-evaluate wall-clock progress on wake/config changes without restarting the fade.
        return renderFrame()
    }

    private fun renderFrame(): Boolean {
        val request = requested ?: return false
        if (!canOverlay) { removeFilter(); return false }
        val instant = now()
        if (request.fade?.finishedWindow(instant) == true) { removeFilter(); return true }
        val progress = (request.fade?.opacityStep(instant) ?: ScheduledFilterFade.STEPS) /
            ScheduledFilterFade.STEPS.toFloat()
        val finalColor = filterColor(request.dim, request.warmth)
        val appearance = if (systemUi) {
            Appearance((finalColor and 0x00FFFFFF) or
                ((Color.alpha(finalColor) * progress).roundToInt() shl 24), 0f, 1f)
        } else {
            Appearance(if (request.warmth == 0) Color.TRANSPARENT else Color.rgb(255, 67, 15),
                request.dim / 100f * progress,
                (if (request.warmth == 0) .01f else request.warmth / 100f * .55f) * progress)
        }
        if (applied != appearance) {
            val view = filter ?: View(context).also { filter = it }
            view.setBackgroundColor(appearance.color)
            val lp = WindowManager.LayoutParams(-1, -1,
                if (systemUi) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    (if (systemUi) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    else WindowManager.LayoutParams.FLAG_DIM_BEHIND), PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                dimAmount = appearance.dimAmount
                alpha = appearance.windowAlpha
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
                title = "Quiet Night screen filter"
            }
            try {
                if (applied == null) wm.addView(view, lp) else wm.updateViewLayout(view, lp)
                applied = appearance
            } catch (_: SecurityException) {
                removeFilter(); return false
            } catch (_: WindowManager.BadTokenException) {
                removeFilter(); return false
            } catch (_: IllegalArgumentException) {
                removeFilter(); return false
            }
        }
        request.fade?.nextUpdateDelay(instant)?.let { handler.postDelayed(advance, it) }
        return true
    }

    private fun removeFilter() {
        handler.removeCallbacks(advance)
        requested = null
        filter?.let { runCatching { wm.removeView(it) } }
        filter = null
        applied = null
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
