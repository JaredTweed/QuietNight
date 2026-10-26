package com.granularvolume.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.PermissionHelper

object SleepScheduler {
    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, 802,
        Intent(context, ScheduleReceiver::class.java).setAction("quietnight.SCHEDULE"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun hasTimingPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun anyEnabled(config: SleepConfig): Boolean = config.screen || config.audio

    /**
     * Screen uses either the connected accessibility overlay or application-overlay access.
     * Audio never waits for screen access.
     */
    fun screenReady(context: Context, config: SleepConfig): Boolean = !config.screen ||
        (ScreenOverlayService.isConnected() || PermissionHelper.canDrawOverlays(context))

    fun timingReady(context: Context, config: SleepConfig): Boolean =
        config.mode == ScheduleMode.ALWAYS || ScreenOverlayService.hasLiveSchedule() || hasTimingPermission(context)

    fun screenRunnable(context: Context, config: SleepConfig): Boolean =
        config.screen && screenReady(context, config) && timingReady(context, config)

    fun audioRunnable(context: Context, config: SleepConfig): Boolean =
        config.audio

    fun ready(context: Context, config: SleepConfig = SleepSettings.read(context)): Boolean =
        anyEnabled(config) && screenReady(context, config) && timingReady(context, config)

    fun reconcile(context: Context) {
        LegacyBrightnessRecovery.restore(context)
        val config = SleepSettings.read(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarm(context)
        manager.cancel(pi)
        val plan = SleepSchedule.plan(config)
        val live = if (config.screen && screenReady(context, config) && ScreenOverlayService.hasLiveSchedule()) {
            ScreenOverlayService.armSchedule(plan.nextChange)
        } else {
            ScreenOverlayService.cancelSchedule()
            false
        }
        val screenOn = screenRunnable(context, config)
        val audioOn = audioRunnable(context, config)
        // Apply trusted filtering immediately on wake; service startup is asynchronous.
        ScreenOverlayService.update(config.copy(screen = screenOn && plan.active))
        if (!screenOn && !audioOn) {
            context.stopService(Intent(context, VolumeControlService::class.java))
            return
        }
        if (config.screen && !live && hasTimingPermission(context)) plan.nextChange?.let {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pi) }
            catch (_: SecurityException) { /* Audio stays independent if timing access was revoked. */ }
        }
        if ((screenOn && plan.active) || audioOn) {
            try { context.startForegroundService(Intent(context, VolumeControlService::class.java)) }
            catch (_: IllegalStateException) { /* Android refused background start; next foreground open retries. */ }
            catch (_: SecurityException) { /* Permission may have just been revoked. */ }
        } else {
            context.stopService(Intent(context, VolumeControlService::class.java))
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { SleepScheduler.reconcile(context) }
}
