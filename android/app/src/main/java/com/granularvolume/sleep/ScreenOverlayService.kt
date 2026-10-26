package com.granularvolume.sleep

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.content.ContextCompat
import java.time.Instant
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/** Provides a trusted, touch-through display filter above Quick Settings without reading screens. */
class ScreenOverlayService : AccessibilityService() {
    private lateinit var overlay: NightOverlay
    private var schedule: LiveScreenSchedule? = null
    private var watchingWake = false
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Listener alarms can be deferred in Doze. Screen-off filtering has no visible
            // effect; reconcile the wall clock immediately on wake and after clock edits.
            if (SleepSettings.read(this@ScreenOverlayService).screen)
                SleepScheduler.reconcile(this@ScreenOverlayService)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (::overlay.isInitialized) overlay.close()
        schedule?.close()
        schedule = LiveScreenSchedule(this) { SleepScheduler.reconcile(this) }
        overlay = NightOverlay(this, systemUi = true)
        if (!watchingWake) {
            ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            watchingWake = true
        }
        connected = this
        SleepScheduler.reconcile(this)
    }

    // No event types, content retrieval, gesture injection or key filtering are requested.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        schedule?.close(); schedule = null
        if (watchingWake) { unregisterReceiver(wakeReceiver); watchingWake = false }
        if (::overlay.isInitialized) overlay.close()
        if (connected === this) {
            connected = null
            SleepScheduler.reconcile(this)
        }
    }

    companion object {
        // Android owns this service; clear the reference on both unbind and destruction.
        private var connected: ScreenOverlayService? = null
        fun isConnected(): Boolean = connected != null
        fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        fun hasLiveSchedule(): Boolean = connected?.schedule?.available == true
        fun armSchedule(boundary: Instant?): Boolean = connected?.schedule?.arm(boundary) == true
        fun cancelSchedule() { connected?.schedule?.cancel() }

        /** Returns whether the trusted service owns the screen filter. Called on the main thread. */
        fun update(config: SleepConfig): Boolean {
            val service = connected ?: return false
            return service.overlay.update(config)
        }
        fun hide() { connected?.overlay?.close() }
    }
}
