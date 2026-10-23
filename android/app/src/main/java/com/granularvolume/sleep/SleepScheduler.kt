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
     * Screen tint and dimming are drawn as an application overlay, so Screen needs the
     * display-over-other-apps permission. Audio never waits for screen access.
     */
    fun screenReady(context: Context, config: SleepConfig): Boolean = !config.screen ||
        PermissionHelper.canDrawOverlays(context)

    fun timingReady(context: Context, config: SleepConfig): Boolean =
        config.mode == ScheduleMode.ALWAYS || hasTimingPermission(context)

    fun screenRunnable(context: Context, config: SleepConfig): Boolean =
        config.screen && screenReady(context, config) && timingReady(context, config)

    fun audioRunnable(context: Context, config: SleepConfig): Boolean =
        config.audio

    fun ready(context: Context, config: SleepConfig = SleepSettings.read(context)): Boolean =
        anyEnabled(config) && screenReady(context, config) && timingReady(context, config)

    fun reconcile(context: Context) {
        val config = SleepSettings.read(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarm(context)
        manager.cancel(pi)
        val plan = SleepSchedule.plan(config)
        val screenOn = screenRunnable(context, config)
        val audioOn = audioRunnable(context, config)
        if (!screenOn && !audioOn) {
            context.stopService(Intent(context, VolumeControlService::class.java))
            return
        }
        if (config.screen && timingReady(context, config)) plan.nextChange?.let {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pi) }
            catch (_: SecurityException) { return }
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
