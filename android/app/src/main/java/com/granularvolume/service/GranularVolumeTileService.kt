package com.granularvolume.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.granularvolume.MainActivity
import com.granularvolume.sleep.*

class GranularVolumeTileService : TileService() {
    override fun onStartListening() { super.onStartListening(); updateTile() }
    // The PendingIntent overload only exists on API 34+. The Intent path is required on older OS versions.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val config = SleepSettings.read(this)
        val next = config.copy(screen = !config.screen)
        if (next.screen && !SleepScheduler.ready(this, next)) {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
            return
        }
        SleepSettings.write(this, next)
        SleepScheduler.reconcile(this); updateTile()
    }
    private fun updateTile() {
        val config = SleepSettings.read(this)
        qsTile?.apply {
            label = "Quiet Night screen"
            state = if (config.screen) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = when {
                !config.screen -> "Off"
                SleepSchedule.plan(config).active -> "On"
                else -> "Scheduled"
            }
            updateTile()
        }
    }
}
