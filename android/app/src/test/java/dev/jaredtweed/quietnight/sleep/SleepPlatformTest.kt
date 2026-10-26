package dev.jaredtweed.quietnight.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.view.WindowManager
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
        val actualWindows = RuntimeEnvironment.getApplication().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        `when`(windows.defaultDisplay).thenReturn(actualWindows.defaultDisplay)
        if (Build.VERSION.SDK_INT >= 31) `when`(alarms.canScheduleExactAlarms()).thenReturn(true)
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
    @Test fun screenControlsWaitForOverlayPermission() {
        ShadowSettings.setCanDrawOverlays(false)
        SleepSettings.write(context, SleepConfig(screen = true))
        SleepScheduler.reconcile(context)
        assertEquals(0, starts); assertEquals(1, stops)
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
    @Test fun exactAlarmRevocationDuringSchedulingCannotPreventIndependentAudioStart() {
        doThrow(SecurityException("revoked")).`when`(alarms)
            .setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent::class.java))
        val minute = LocalTime.now().hour * 60 + LocalTime.now().minute
        SleepSettings.write(context, SleepConfig(screen = true, audio = true, mode = ScheduleMode.CUSTOM,
            start = (minute + 60) % 1440, end = (minute + 120) % 1440))
        SleepScheduler.reconcile(context)
        assertEquals(1, starts)
    }

    @Test fun screenSettingsCannotOverwriteBluetoothFloor() {
        dev.jaredtweed.quietnight.util.Prefs.setBluetoothFloor(context, "BX27", 2)
        SleepSettings.write(context, SleepConfig(screen = true, audio = true))
        SleepSettings.update(context) { it.copy(screen = false, warmth = 80, dim = 85) }
        assertTrue(SleepSettings.read(context).audio)
        assertEquals(2, dev.jaredtweed.quietnight.util.Prefs.getBluetoothFloor(context, "BX27"))
    }
    @Test fun legacyActivationMigratesOnceWithoutCouplingNewFlags() {
        SleepSettings.prefs(context).edit().putBoolean("enabled", true).putBoolean("quiet", true).commit()
        val previous = SleepSettings.read(context)
        assertTrue(previous.screen); assertTrue(previous.audio)
        SleepSettings.write(context, previous.copy(screen = false))
        assertFalse(SleepSettings.read(context).screen)
        assertTrue(SleepSettings.read(context).audio)
    }
    @Test fun maximumDimUsesOverlayDimLayerAndTranslucentTouchThroughWindow() {
        val overlay = NightOverlay(context)
        overlay.update(SleepConfig(screen = true, dim = 85, warmth = 100))
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windows).addView(any(android.view.View::class.java), params.capture())
        assertEquals(.85f, params.value.dimAmount)
        assertTrue(params.value.alpha <= .8f)
        assertTrue(params.value.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.value.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
    }
    @Test @Config(sdk = [28, 33])
    fun trustedOverlayDimsSystemUiWithAnActualBlackFilterAndLeavesBrightnessAlone() {
        android.provider.Settings.System.putInt(context.contentResolver, "screen_brightness", 123)
        android.provider.Settings.System.putInt(context.contentResolver, "screen_brightness_mode", 1)
        ShadowSettings.setCanDrawOverlays(false)
        NightOverlay(context, systemUi = true).update(SleepConfig(screen = true, dim = 85, warmth = 0))
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        val view = ArgumentCaptor.forClass(android.view.View::class.java)
        verify(windows).addView(view.capture(), params.capture())
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.value.type)
        assertTrue(params.value.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        if (Build.VERSION.SDK_INT >= 30) assertEquals(0, params.value.fitInsetsTypes)
        val color = (view.value.background as android.graphics.drawable.ColorDrawable).color
        assertEquals(217, android.graphics.Color.alpha(color))
        assertEquals(0, android.graphics.Color.red(color))
        assertEquals(123, android.provider.Settings.System.getInt(context.contentResolver, "screen_brightness"))
        assertEquals(1, android.provider.Settings.System.getInt(context.contentResolver, "screen_brightness_mode"))
    }
    @Test fun accessibilityDisconnectReleasesOwnershipAndRequiresFallbackAccess() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = org.robolectric.Robolectric.buildService(ScreenOverlayService::class.java).create()
        try {
            val service = controller.get()
            val connect = ScreenOverlayService::class.java.getDeclaredMethod("onServiceConnected")
            connect.isAccessible = true
            connect.invoke(service)
            assertTrue(ScreenOverlayService.isConnected())
            assertTrue(SleepScheduler.screenReady(context, SleepConfig(screen = true)))
            assertTrue(ScreenOverlayService.update(SleepConfig(screen = false)))
            service.onUnbind(null)
            assertFalse(ScreenOverlayService.isConnected())
            assertFalse(SleepScheduler.screenReady(context, SleepConfig(screen = true)))
        } finally { controller.destroy() }
    }

    @Test fun accessibilityServiceRequestsOnlyKeyCapabilityAndNoScreenContentOrGestures() {
        val parser = context.resources.getXml(dev.jaredtweed.quietnight.R.xml.screen_overlay_service)
        parser.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals("false", it.getAttributeValue(ns, "canRetrieveWindowContent"))
            assertNull(it.getAttributeValue(ns, "accessibilityEventTypes"))
            assertNull(it.getAttributeValue(ns, "accessibilityFlags"))
            assertNull(it.getAttributeValue(ns, "canPerformGestures"))
            assertEquals("true", it.getAttributeValue(ns, "canRequestFilterKeyEvents"))
        }
    }

    @Test fun trustedFilterComposesTintUnderDimmingAndZeroRemovesBoth() {
        val dimOnly = NightOverlay.filterColor(85, 0)
        assertEquals(0, android.graphics.Color.red(dimOnly))
        assertEquals(android.graphics.Color.TRANSPARENT, NightOverlay.filterColor(0, 0))
        val warm = NightOverlay.filterColor(0, 100)
        val dimWarm = NightOverlay.filterColor(85, 100)
        assertTrue(android.graphics.Color.red(dimWarm) < android.graphics.Color.red(warm))
        assertTrue(android.graphics.Color.alpha(dimWarm) > android.graphics.Color.alpha(warm))
    }

    @Test fun warmthWithoutDimDoesNotApplyADimLayer() {
        val overlay = NightOverlay(context)
        overlay.update(SleepConfig(screen = true, dim = 0, warmth = 60))
        val params = ArgumentCaptor.forClass(WindowManager.LayoutParams::class.java)
        verify(windows).addView(any(android.view.View::class.java), params.capture())
        assertEquals(0f, params.value.dimAmount)
        assertTrue(params.value.alpha <= .8f)
    }
    @Test fun screenOverlayIsRemovedWhenScreenControlsAreOff() {
        val overlay = NightOverlay(context)
        overlay.update(SleepConfig(screen = true, dim = 50, warmth = 50))
        overlay.update(SleepConfig(screen = false, dim = 50, warmth = 50))
        verify(windows).removeView(any(android.view.View::class.java))
    }
    @Test fun preferencesClampRestoredOutOfRangeDimAndWarmth() {
        SleepSettings.prefs(context).edit().putInt("dim", 100).putInt("warmth", -12).commit()
        val config = SleepSettings.read(context)
        assertEquals(85, config.dim); assertEquals(0, config.warmth)
    }

    @Test fun unchangedFiltersAvoidWindowUpdatesAndAudioChangesDoNotRedrawTheFilter() {
        val overlay = NightOverlay(context)
        val config = SleepConfig(screen = true, dim = 50, warmth = 50)
        assertTrue(overlay.update(config))
        repeat(10) { assertTrue(overlay.update(config.copy(audio = it % 2 == 0))) }
        verify(windows, times(1)).addView(any(android.view.View::class.java), any(WindowManager.LayoutParams::class.java))
        verify(windows, never()).updateViewLayout(any(android.view.View::class.java), any(WindowManager.LayoutParams::class.java))
        assertTrue(overlay.update(config.copy(dim = 51)))
        verify(windows, times(1)).updateViewLayout(any(android.view.View::class.java), any(WindowManager.LayoutParams::class.java))
        overlay.close(); overlay.close()
        verify(windows, times(1)).removeView(any(android.view.View::class.java))
    }

    @Test fun rejectedOverlayDoesNotClaimOwnershipAndCanBeRetried() {
        val overlay = NightOverlay(context, systemUi = true)
        val config = SleepConfig(screen = true)
        doThrow(SecurityException("Access revoked")).`when`(windows)
            .addView(any(android.view.View::class.java), any(WindowManager.LayoutParams::class.java))
        assertFalse(overlay.update(config))
        doNothing().`when`(windows).addView(any(android.view.View::class.java), any(WindowManager.LayoutParams::class.java))
        assertTrue(overlay.update(config))
        overlay.close()
    }

}
