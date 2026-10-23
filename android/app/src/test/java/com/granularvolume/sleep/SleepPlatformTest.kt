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
        SleepSettings.write(context, SleepConfig(screen = true))
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
        SleepSettings.write(context, SleepConfig(screen = true, mode = ScheduleMode.CUSTOM))
        SleepScheduler.reconcile(context)
        assertEquals(0, starts); assertEquals(1, stops)
        verify(alarms, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent::class.java))
    }
    @Test fun futureCustomBoundaryArmsExactAlarmWithoutRunningDaytimeService() {
        val minute = LocalTime.now().hour * 60 + LocalTime.now().minute
        SleepSettings.write(context, SleepConfig(screen = true, mode = ScheduleMode.CUSTOM,
            start = (minute + 60) % 1440, end = (minute + 120) % 1440))
        SleepScheduler.reconcile(context)
        verify(alarms).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent::class.java))
        assertEquals(0, starts); assertEquals(1, stops)
    }
    @Test fun audioOnlyStartsWithoutOverlayOrSettingsAccess() {
        ShadowSettings.setCanDrawOverlays(false)
        SleepSettings.write(context, SleepConfig(audio = true))
        SleepScheduler.reconcile(context)
        assertEquals(1, starts)
    }
    @Test fun screenControlsDoNotNeedOverlayWhenSystemDimmingIsAvailable() {
        ShadowSettings.setCanDrawOverlays(false)
        SleepSettings.write(context, SleepConfig(screen = true))
        SleepScheduler.reconcile(context)
        // Robolectric grants settings access. Real devices without either permission stay stopped.
        assertEquals(1, starts)
    }
    @Test fun screenAndAudioFlagsAreIndependent() {
        SleepSettings.write(context, SleepConfig(screen = true, audio = false))
        assertTrue(SleepScheduler.ready(context))
        SleepSettings.write(context, SleepConfig(screen = false, audio = true))
        assertTrue(SleepScheduler.ready(context))
        SleepSettings.write(context, SleepConfig(screen = false, audio = false))
        assertFalse(SleepScheduler.ready(context))
    }
    @Test fun audioRunsOutsideScreenWindowWithoutExactAlarmAccess() {
        `when`(alarms.canScheduleExactAlarms()).thenReturn(false)
        val config = SleepConfig(screen = true, audio = true, mode = ScheduleMode.CUSTOM, start = 0, end = 1)
        assertTrue(SleepScheduler.audioRunnable(context, config))
        assertFalse(SleepScheduler.screenRunnable(context, config))
        SleepSettings.write(context, config)
        SleepScheduler.reconcile(context)
        assertEquals(1, starts)
    }
    @Test fun screenSettingsCannotOverwriteBluetoothFloor() {
        com.granularvolume.util.Prefs.setBluetoothFloor(context, "BX27", 2)
        SleepSettings.write(context, SleepConfig(screen = true, audio = true))
        SleepSettings.update(context) { it.copy(screen = false, warmth = 80, dim = 85) }
        assertTrue(SleepSettings.read(context).audio)
        assertEquals(2, com.granularvolume.util.Prefs.getBluetoothFloor(context, "BX27"))
    }
    @Test fun legacyActivationMigratesOnceWithoutCouplingNewFlags() {
        SleepSettings.prefs(context).edit().putBoolean("enabled", true).putBoolean("quiet", true).commit()
        val previous = SleepSettings.read(context)
        assertTrue(previous.screen); assertTrue(previous.audio)
        SleepSettings.write(context, previous.copy(screen = false))
        assertFalse(SleepSettings.read(context).screen)
        assertTrue(SleepSettings.read(context).audio)
    }
    @Test fun maximumDimUsesSystemDimLayerAndTranslucentTouchThroughWindow() {
        val overlay = NightOverlay(context)
        overlay.update(SleepConfig(screen = true, dim = 85, warmth = 100), "0 dB", {}, {})
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windows).addView(any(android.view.View::class.java), params.capture())
        assertEquals(.85f, params.value.dimAmount)
        assertTrue(params.value.alpha <= .8f)
        assertTrue(params.value.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.value.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
    }
    @Test fun systemDimmingDoesNotAlsoApplyAnAppDimLayer() {
        val overlay = NightOverlay(context)
        overlay.update(SleepConfig(screen = true, dim = 0, warmth = 60), "0 dB", {}, {})
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windows).addView(any(android.view.View::class.java), params.capture())
        assertEquals(0f, params.value.dimAmount)
        assertTrue(params.value.alpha <= .8f)
    }
    @Test fun screenOverlayIsRemovedWhenScreenControlsAreOff() {
        val overlay = NightOverlay(context)
        overlay.update(SleepConfig(screen = true, dim = 50, warmth = 50), "0 dB", {}, {})
        overlay.update(SleepConfig(screen = false, dim = 50, warmth = 50), "0 dB", {}, {})
        verify(windows).removeView(any(android.view.View::class.java))
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
