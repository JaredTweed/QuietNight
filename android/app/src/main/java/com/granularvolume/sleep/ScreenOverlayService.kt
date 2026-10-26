package com.granularvolume.sleep

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/** Provides a trusted, touch-through display filter above Quick Settings without reading screens. */
class ScreenOverlayService : AccessibilityService() {
    private lateinit var overlay: NightOverlay

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (::overlay.isInitialized) overlay.close()
        overlay = NightOverlay(this, systemUi = true)
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

        /** Returns whether the trusted service owns the screen filter. Called on the main thread. */
        fun update(config: SleepConfig): Boolean {
            val service = connected ?: return false
            return service.overlay.update(config)
        }
        fun hide() { connected?.overlay?.close() }
    }
}
