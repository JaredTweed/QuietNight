package com.granularvolume.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.view.WindowManager
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SleepPlatformTest {
    private lateinit var context: Context
    private lateinit var alarms: AlarmManager
    private lateinit var windows: WindowManager
    private var starts = 0
    private var stops = 0
    @Before fun setup() {
        alarms = mock(AlarmManager::class.java); windows = mock(WindowManager::class.java)
        `when`(alarms.canScheduleExactAlarms()).thenReturn(true)
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = when (name) {
                Context.ALARM_SERVICE -> alarms
                Context.WINDOW_SERVICE -> windows
                else -> super.getSystemService(name)
            }
            override fun startForegroundService(intent: Intent): ComponentName? { starts++; return intent.component }
            override fun stopService(intent: Intent): Boolean { stops++; return true }
        }
        SleepSettings.prefs(context).edit().clear().commit()
        context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
    }
    @Test fun alwaysStartsWithoutAskingForExactAlarms() {
        `when`(alarms.canScheduleExactAlarms()).thenReturn(false)
        SleepSettings.write(context, SleepConfig(enabled = true))
        SleepScheduler.reconcile(context)
        assertEquals(1, starts)
        verify(alarms, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent::class.java))
    }
    @Test fun pauseCancelsFutureAlarmAndStopsEffects() {
        SleepScheduler.reconcile(context)
        verify(alarms).cancel(any(PendingIntent::class.java))
        assertEquals(0, starts); assertEquals(1, stops)
    }
    @Test fun customWaitsForTimingAccessInsteadOfPretendingScheduleIsArmed() {
        `when`(alarms.canScheduleExactAlarms()).thenReturn(false)
        SleepSettings.write(context, SleepConfig(enabled = true, mode = ScheduleMode.CUSTOM))
        SleepScheduler.reconcile(context)
        assertEquals(0, starts); assertEquals(1, stops)
        verify(alarms, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent::class.java))
    }
    @Test fun futureCustomBoundaryArmsExactAlarmWithoutRunningDaytimeService() {
        val minute = LocalTime.now().hour * 60 + LocalTime.now().minute
        SleepSettings.write(context, SleepConfig(enabled = true, mode = ScheduleMode.CUSTOM,
            start = (minute + 60) % 1440, end = (minute + 120) % 1440))
        SleepScheduler.reconcile(context)
        verify(alarms).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent::class.java))
        assertEquals(0, starts); assertEquals(1, stops)
    }
    @Test fun missingOverlayPermissionNeverStartsEffects() {
        ShadowSettings.setCanDrawOverlays(false)
        SleepSettings.write(context, SleepConfig(enabled = true))
        SleepScheduler.reconcile(context)
        assertEquals(0, starts)
    }
    @Test fun maximumDimUsesSystemDimLayerAndTranslucentTouchThroughWindow() {
        val overlay = NightOverlay(context)
        overlay.update(SleepConfig(dim = 85, warmth = 100), "0 dB", {}, {})
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windows).addView(any(android.view.View::class.java), params.capture())
        assertEquals(.85f, params.value.dimAmount)
        assertTrue(params.value.alpha <= .8f)
        assertTrue(params.value.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.value.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
    }
    @Test fun preferencesClampRestoredOutOfRangeDimAndWarmth() {
        SleepSettings.prefs(context).edit().putInt("dim", 100).putInt("warmth", -12).commit()
        val config = SleepSettings.read(context)
        assertEquals(85, config.dim); assertEquals(0, config.warmth)
    }
    @Test fun publicBrightnessAdapterRestoresSystemBrightnessAndAdaptiveMode() {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 200)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 1)
        assertTrue(ScreenDimming.canWrite(context))
        assertEquals(ScreenDimming.Mode.SYSTEM, ScreenDimming.apply(context, 75).mode)
        assertEquals(50, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(0, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
        assertTrue(ScreenDimming.restore(context))
        assertEquals(200, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(1, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
    }
}
