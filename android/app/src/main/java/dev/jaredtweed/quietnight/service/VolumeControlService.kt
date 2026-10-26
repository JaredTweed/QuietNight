package dev.jaredtweed.quietnight.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.jaredtweed.quietnight.MainActivity
import dev.jaredtweed.quietnight.R
import dev.jaredtweed.quietnight.audio.*
import dev.jaredtweed.quietnight.sleep.*
import dev.jaredtweed.quietnight.util.*

/** Owns only the active wind-down session. AlarmManager owns future start/stop boundaries. */
class VolumeControlService : Service() {
    companion object {
        const val ACTION_STOP = "dev.jaredtweed.quietnight.ACTION_STOP"
        const val ACTION_MUTE_MEDIA = "quietnight.MUTE_MEDIA"
        const val ACTION_CALIBRATE_BLUETOOTH_FLOOR = "dev.jaredtweed.quietnight.ACTION_CALIBRATE_BLUETOOTH_FLOOR"
        const val EXTRA_BLUETOOTH_NAME = "bluetooth_name"
        const val EXTRA_BLUETOOTH_FLOOR = "bluetooth_floor"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val schedule = SleepPlanCache()
    private var audio: AudioController? = null
    private var coordinator: FullRangeCoordinator? = null
    private lateinit var overlay: NightOverlay
    private lateinit var am: AudioManager
    private var notificationFeatures: Pair<Boolean, Boolean>? = null
    private val applyLater = Runnable { applyConfig() }
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
        val config = SleepSettings.read(this)
        val n = notification(config)
        notificationFeatures = config.screen to config.audio
        if (Build.VERSION.SDK_INT >= 34) startForeground(802, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(802, n)
        SleepSettings.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        ContextCompat.registerReceiver(this, volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        am.registerAudioDeviceCallback(devices, handler)
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
        coordinator?.let { c ->
            if (intent?.action == ACTION_MUTE_MEDIA) c.muteMedia()
            if (intent?.action == ACTION_CALIBRATE_BLUETOOTH_FLOOR) intent.getStringExtra(EXTRA_BLUETOOTH_NAME)?.let {
                c.calibrateBluetoothFloor(it, intent.getIntExtra(EXTRA_BLUETOOTH_FLOOR, 1))
            }
        }
        publishAudio()
        return START_STICKY
    }
    private fun applyConfig(): Boolean {
        val config = SleepSettings.read(this)
        val plan = schedule.plan(config)
        val screenOn = SleepScheduler.screenRunnable(this, config) && plan.active
        val audioOn = SleepScheduler.audioRunnable(this, config)
        if (!screenOn && !audioOn) { stopSelf(); return false }
        if (audioOn && audio == null) {
            val a = AudioController(applicationContext)
            val v = StreamVolumeController(applicationContext)
            val c = FullRangeCoordinator(applicationContext, a, v)
            c.onQuietUnavailable = { android.widget.Toast.makeText(this, "Quiet steps cannot change cellular call audio", android.widget.Toast.LENGTH_SHORT).show() }
            audio = a; coordinator = c
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
        publishAudio()
        val display = config.copy(screen = screenOn)
        if (ScreenOverlayService.update(display)) overlay.close() else overlay.update(display)
        val features = screenOn to audioOn
        if (notificationFeatures != features) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(802,
                notification(config.copy(screen = screenOn, audio = audioOn)))
            notificationFeatures = features
        }
        return true
    }
    private fun publishAudio() { ControlLive.audioState = coordinator?.uiState() }
    private fun releaseAudio() { ControlLive.audioState = null; coordinator?.dispose(); coordinator = null; audio?.release(); audio = null; ControlLive.effect = ControlLive.Effect.UNKNOWN }
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
        runCatching { unregisterReceiver(volumeReceiver) }; am.unregisterAudioDeviceCallback(devices)
        if (Build.VERSION.SDK_INT >= 31 && modeListener != null) am.removeOnModeChangedListener(modeListener)
        overlay.close(); ScreenOverlayService.hide(); releaseAudio(); ControlLive.reset()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
