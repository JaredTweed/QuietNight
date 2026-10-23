package com.granularvolume.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import android.database.ContentObserver
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.granularvolume.MainActivity
import com.granularvolume.R
import com.granularvolume.audio.*
import com.granularvolume.sleep.*
import com.granularvolume.util.*

/** Owns only the active wind-down session. AlarmManager owns future start/stop boundaries. */
class VolumeControlService : Service() {
    companion object {
        const val ACTION_STOP = "com.granularvolume.ACTION_STOP"
        const val ACTION_SET_GAIN = "quietnight.SET_GAIN"
        const val ACTION_QUIETER = "quietnight.QUIETER"
        const val ACTION_LOUDER = "quietnight.LOUDER"
        const val ACTION_MUTE = "quietnight.MUTE"
        const val ACTION_CALIBRATE_BLUETOOTH_FLOOR = "com.granularvolume.ACTION_CALIBRATE_BLUETOOTH_FLOOR"
        const val EXTRA_BLUETOOTH_NAME = "bluetooth_name"
        const val EXTRA_BLUETOOTH_FLOOR = "bluetooth_floor"
    }
    private val handler = Handler(Looper.getMainLooper())
    private var audio: AudioController? = null
    private var volume: StreamVolumeController? = null
    private var coordinator: FullRangeCoordinator? = null
    private lateinit var overlay: NightOverlay
    private lateinit var am: AudioManager
    private val applyLater = Runnable { applyConfig() }
    private val brightnessChanged = Runnable {
        if (ScreenDimming.checkForUserChange(this)) applyConfig()
    }
    private val brightnessObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(brightnessChanged)
            handler.postDelayed(brightnessChanged, 120)
        }
    }
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.removeCallbacks(applyLater); handler.postDelayed(applyLater, 80)
    }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return
            val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            val to = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
            val from = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", to)
            if (stream >= 0 && to >= 0) {
                coordinator?.onExternalVolumeChange(stream, from, to)
                publishAudio()
            }
        }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>?) = routeChanged()
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>?) = routeChanged()
    }
    private val modeListener = if (Build.VERSION.SDK_INT >= 31) AudioManager.OnModeChangedListener {
        coordinator?.onAudioModeChanged(); publishAudio()
    } else null
    private fun routeChanged() { coordinator?.refreshCurve(); coordinator?.onRouteChanged(); publishAudio() }

    override fun onCreate() {
        super.onCreate()
        overlay = NightOverlay(this)
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel("quiet_night", "Wind-down controls", NotificationManager.IMPORTANCE_LOW))
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(802, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(802, n)
        SleepSettings.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        ContextCompat.registerReceiver(this, volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        am.registerAudioDeviceCallback(devices, handler)
        contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver)
        contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, brightnessObserver)
        if (Build.VERSION.SDK_INT >= 31 && modeListener != null) am.addOnModeChangedListener({ handler.post(it) }, modeListener)
        ControlLive.running = true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SleepSettings.update(this) { it.copy(screen = false, audio = false) }
            SleepScheduler.reconcile(this)
            stopSelf(); return START_NOT_STICKY
        }
        if (!applyConfig()) return START_NOT_STICKY
        val c = coordinator
        when (intent?.action) {
            ACTION_SET_GAIN -> {
                val db = intent.getFloatExtra("db", 0f)
                if (db < 0) c?.applyQuiet(db.coerceIn(-30f, 0f))
                else c?.applyUpper((c.upperPositionCount() - 1).coerceAtLeast(0))
            }
            ACTION_QUIETER -> step(-1)
            ACTION_LOUDER -> step(1)
            ACTION_MUTE -> c?.toggleMute()
            ACTION_CALIBRATE_BLUETOOTH_FLOOR -> intent.getStringExtra(EXTRA_BLUETOOTH_NAME)?.let {
                c?.calibrateBluetoothFloor(it, intent.getIntExtra(EXTRA_BLUETOOTH_FLOOR, 1))
            }
        }
        publishAudio()
        return START_STICKY
    }
    private fun step(direction: Int) {
        val c = coordinator ?: return
        val state = c.uiState()
        if (state.zoneQuiet) {
            val db = state.quietDb + direction * 5f
            if (db >= 0) c.applyUpper(c.upperPositionCount() - 1) else c.applyQuiet(db.coerceAtLeast(-30f))
        } else if (direction < 0 && state.upperPos >= state.upperCount - 1) c.applyQuiet(-5f)
        else c.applyUpper((state.upperPos - direction).coerceIn(0, state.upperCount - 1))
        publishAudio()
    }
    private fun applyConfig(): Boolean {
        val config = SleepSettings.read(this)
        val plan = SleepSchedule.plan(config)
        val screenOn = SleepScheduler.screenRunnable(this, config) && plan.active
        val audioOn = SleepScheduler.audioRunnable(this, config)
        if (!screenOn && !audioOn) { stopSelf(); return false }
        if (audioOn && audio == null) {
            val a = AudioController(applicationContext)
            val v = StreamVolumeController(applicationContext)
            val c = FullRangeCoordinator(applicationContext, a, v)
            c.volumeDownProvider = { SleepSettings.read(this).volumeDown }
            c.onQuietUnavailable = { android.widget.Toast.makeText(this, "Quiet steps cannot change cellular call audio", android.widget.Toast.LENGTH_SHORT).show() }
            audio = a; volume = v; coordinator = c
            a.onEffectState = { available, preferred ->
                ControlLive.effect = if (!available) ControlLive.Effect.NONE else if (preferred)
                    ControlLive.Effect.PREFERRED else ControlLive.Effect.FALLBACK
            }
            a.initialize(); c.syncZoneToAppliedGain(); c.refreshCurve()
            // A scheduled start must not unmute media or raise a headset's silent step.
            if (c.zoneQuiet && a.attenuationDb.value < 0f &&
                v.index(AudioManager.STREAM_MUSIC) >= v.minAudibleIndex(AudioManager.STREAM_MUSIC))
                c.applyQuiet(a.attenuationDb.value)
            ControlLive.effect = if (!a.isEffectAvailable) ControlLive.Effect.NONE else if (a.usingPreferredStrategy) ControlLive.Effect.PREFERRED else ControlLive.Effect.FALLBACK
        } else if (!audioOn && audio != null) releaseAudio()
        val dimming = if (screenOn) {
            ScreenDimming.apply(this, config.dim)
        } else {
            val restored = ScreenDimming.restore(this)
            ScreenDimming.Result(if (restored) ScreenDimming.Mode.OFF else ScreenDimming.Mode.RESTORE_NEEDED, 0)
        }
        publishAudio()
        ControlLive.dimming = dimming.mode
        // System brightness already dims the panel. Do not also apply an app dim layer.
        overlay.update(config.copy(screen = screenOn, dim = dimming.overlayDim), "${audio?.attenuationDb?.value?.toInt() ?: 0} dB", { step(-1) }, { step(1) })
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(802, notification(config.copy(screen = screenOn, audio = audioOn)))
        return true
    }
    private fun publishAudio() { ControlLive.audioState = coordinator?.uiState() }
    private fun releaseAudio() { ControlLive.audioState = null; coordinator?.dispose(); coordinator = null; audio?.release(); audio = null; volume = null; ControlLive.effect = ControlLive.Effect.UNKNOWN }
    private fun notification(config: SleepConfig = SleepSettings.read(this)): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2, Intent(this, VolumeControlService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val detail = when {
            config.screen && config.audio -> "Screen and sound, softened for the evening"
            config.screen -> "Your screen is softened for the evening"
            else -> "Quiet audio controls are on"
        }
        return NotificationCompat.Builder(this, "quiet_night").setSmallIcon(R.drawable.ic_qs_tile)
            .setContentTitle("Quiet Night is on").setContentText(detail)
            .setContentIntent(open).addAction(0, "Stop", stop).setOngoing(true).setSilent(true).build()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        SleepSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        contentResolver.unregisterContentObserver(brightnessObserver)
        runCatching { unregisterReceiver(volumeReceiver) }; am.unregisterAudioDeviceCallback(devices)
        if (Build.VERSION.SDK_INT >= 31 && modeListener != null) am.removeOnModeChangedListener(modeListener)
        overlay.close(); ScreenDimming.restore(this); releaseAudio(); ControlLive.reset()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
