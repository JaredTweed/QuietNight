package dev.jaredtweed.quietnight.audio

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** Observes only volume keys; normal Android key handling always continues. */
internal class VolumeDownHoldDetector(
    private val enabled: () -> Boolean,
    private val mute: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private data class Press(val deviceId: Int, val downTime: Long, var fired: Boolean = false)
    private var press: Press? = null
    private val holdDeadline = Runnable { muteIfStillHeld() }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && event.keyCode != KeyEvent.KEYCODE_VOLUME_UP)
            return false
        if (!enabled() || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            cancel()
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val current = press
                if (event.repeatCount == 0 &&
                    (current == null || current.deviceId != event.deviceId || current.downTime != event.downTime)) {
                    cancel()
                    press = Press(event.deviceId, event.downTime)
                    handler.postAtTime(holdDeadline, event.downTime + HOLD_DURATION_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                val current = press
                if (current != null && current.deviceId == event.deviceId && current.downTime == event.downTime) {
                    if (!event.isCanceled && event.eventTime - current.downTime >= HOLD_DURATION_MS) muteIfStillHeld()
                    cancel()
                }
            }
        }
        return false
    }

    private fun muteIfStillHeld() {
        val current = press ?: return
        if (!enabled()) { cancel(); return }
        if (current.fired) return
        current.fired = true
        mute()
    }

    fun cancel() { handler.removeCallbacks(holdDeadline); press = null }

    companion object { const val HOLD_DURATION_MS = 2_000L }
}
