package com.granularvolume.sleep

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.*
import android.widget.*
import com.granularvolume.MainActivity

/**
 * A translucent, non-interactive tint plus Android's dim layer for ordinary apps.
 *
 * System-wide dimming is handled separately by [ScreenDimming], which changes the
 * device brightness and therefore also covers Quick Settings, notifications and the
 * keyguard. This window is only the warm tint and the fallback app dim layer.
 * It never intercepts app touches.
 */
class NightOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var filter: View? = null
    private var bubble: View? = null
    private fun dp(x: Int) = (x * context.resources.displayMetrics.density).toInt()
    private val canOverlay: Boolean get() = Settings.canDrawOverlays(context)

    fun update(config: SleepConfig, quietLabel: String, onDown: () -> Unit, onUp: () -> Unit) {
        if (!canOverlay) {
            removeFilter(); removeBubble(); return
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
                // Dim via the system dim layer, not an opaque touch-blocking overlay.
                // Even at maximum warmth this window stays well below Android's 0.8 touch limit.
                alpha = if (config.warmth == 0) 0.01f else
                    (config.warmth / 100f * 0.55f).coerceAtMost(0.55f)
                title = "Quiet Night screen filter"
            }
            if (view.parent == null) wm.addView(view, lp) else wm.updateViewLayout(view, lp)
        }
        if (!config.audio || !config.bubble) removeBubble() else if (bubble == null) {
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER; setPadding(dp(4), dp(2), dp(4), dp(2))
                background = GradientDrawable().apply { setColor(Color.rgb(38, 30, 44)); cornerRadius = dp(24).toFloat() }
                elevation = dp(8).toFloat()
            }
            fun key(label: String, description: String, action: () -> Unit) = TextView(context).apply {
                text = label; textSize = 22f; setTextColor(Color.rgb(246, 212, 177)); gravity = Gravity.CENTER
                contentDescription = description; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                setOnClickListener { action() }
            }
            row.addView(key("−", "Quieter", onDown))
            row.addView(key("☾", "Open Quiet Night") {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            row.addView(key("+", "Louder", onUp))
            val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(10); title = "Quiet Night audio controls"
            }
            wm.addView(row, lp); bubble = row
        }
        bubble?.contentDescription = "Quiet Dial, $quietLabel"
    }

    private fun removeFilter() {
        filter?.let { runCatching { wm.removeView(it) } }; filter = null
    }
    private fun removeBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }; bubble = null
    }
    fun close() { removeFilter(); removeBubble() }
}
