package com.granularvolume.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.provider.Settings
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.PermissionHelper

object SleepScheduler {
    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, 802,
        Intent(context, ScheduleReceiver::class.java).setAction("quietnight.SCHEDULE"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun hasTimingPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    fun ready(context: Context, config: SleepConfig = SleepSettings.read(context)): Boolean =
        PermissionHelper.canDrawOverlays(context) &&
            (config.mode == ScheduleMode.ALWAYS || hasTimingPermission(context))
    fun reconcile(context: Context) {
        val config = SleepSettings.read(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarm(context)
        manager.cancel(pi)
        val plan = SleepSchedule.plan(config)
        if (!config.enabled || !ready(context, config)) {
            context.stopService(Intent(context, VolumeControlService::class.java))
            return
        }
        plan.nextChange?.let {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pi) }
            catch (_: SecurityException) { return }
        }
        if (plan.active) {
            try { context.startForegroundService(Intent(context, VolumeControlService::class.java)) }
            catch (_: IllegalStateException) { /* Android refused background start; next foreground open retries. */ }
            catch (_: SecurityException) { /* Overlay permission may have just been revoked. */ }
        } else context.stopService(Intent(context, VolumeControlService::class.java))
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { SleepScheduler.reconcile(context) }
}
