package dev.jaredtweed.quietnight.sleep

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import dev.jaredtweed.quietnight.audio.StreamVolumeController
import dev.jaredtweed.quietnight.audio.VolumeDownHoldDetector
import dev.jaredtweed.quietnight.service.VolumeControlService
import dev.jaredtweed.quietnight.util.ControlLive
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
    private var audioPreferencesRegistered = false
    private val streamVolume by lazy { StreamVolumeController(applicationContext) }
    private val volumeDownHoldDetector = VolumeDownHoldDetector(
        enabled = { SleepSettings.read(this).audio && ControlLive.running &&
            ControlLive.audioState != null && !streamVolume.inCall() },
        mute = {
            startService(Intent(this, VolumeControlService::class.java)
                .setAction(VolumeControlService.ACTION_MUTE_MEDIA))
        }
    )
    private val audioPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "audio_enabled") updateVolumeKeyHandling()
    }

    private fun updateVolumeKeyHandling() {
        val enabled = SleepSettings.read(this).audio
        if (!enabled) volumeDownHoldDetector.cancel()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        val desired = if (enabled) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        // Always publish the latest choice, even if an earlier binder update is still in flight.
        info.flags = desired
        serviceInfo = info
    }
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
        if (!audioPreferencesRegistered) {
            SleepSettings.prefs(this).registerOnSharedPreferenceChangeListener(audioPreferenceListener)
            audioPreferencesRegistered = true
        }
        updateVolumeKeyHandling()
        connected = this
        SleepScheduler.reconcile(this)
    }

    // No content events are requested. Other hardware keys are immediately passed through;
    // their text/characters are never inspected, recorded, or sent anywhere.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onKeyEvent(event: KeyEvent): Boolean = volumeDownHoldDetector.onKeyEvent(event)
    override fun onInterrupt() { volumeDownHoldDetector.cancel() }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        volumeDownHoldDetector.cancel()
        if (audioPreferencesRegistered) {
            SleepSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(audioPreferenceListener)
            audioPreferencesRegistered = false
        }
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
        fun update(config: SleepConfig, plan: SleepPlan? = null): Boolean {
            val service = connected ?: return false
            return service.overlay.update(config, plan)
        }
        fun hide() { connected?.overlay?.close() }
    }
}
