package com.granularvolume.sleep

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveScreenScheduleTest {
    private lateinit var manager: AlarmManager
    private lateinit var context: Context
    @Before fun setup() {
        manager = mock(AlarmManager::class.java)
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) manager else super.getSystemService(name)
        }
        SleepSettings.prefs(context).edit().clear().commit()
    }
    @Test fun boundTimerUsesPermissionFreeListenerAndDoesNotRescheduleUnchangedBoundaries() {
        `when`(manager.canScheduleExactAlarms()).thenReturn(false)
        var calls = 0
        val timer = LiveScreenSchedule(context) { calls++ }
        val boundary = Instant.now().minusSeconds(1)
        assertTrue(timer.arm(boundary)); assertTrue(timer.arm(boundary))
        val listener = ArgumentCaptor.forClass(AlarmManager.OnAlarmListener::class.java)
        verify(manager, times(1)).setExact(eq(AlarmManager.RTC_WAKEUP), eq(boundary.toEpochMilli()),
            anyString(), listener.capture(), any(Handler::class.java))
        listener.value.onAlarm()
        assertEquals(1, calls)
        assertTrue(timer.arm(boundary))
        timer.close()
        listener.value.onAlarm()
        assertEquals(1, calls)
        assertFalse(timer.arm(boundary))
    }
    @Test @Config(sdk = [28, 31, 33, 34]) fun timerEligibilityMatchesThePlatformExemption() {
        val timer = LiveScreenSchedule(context) {}
        assertEquals(android.os.Build.VERSION.SDK_INT < 31 || android.os.Build.VERSION.SDK_INT >= 34,
            timer.available)
        timer.close()
    }
    @Test fun failedRegistrationDoesNotPretendTheLiveScheduleIsArmed() {
        doThrow(SecurityException("not allowed")).`when`(manager).setExact(anyInt(), anyLong(),
            anyString(), any(AlarmManager.OnAlarmListener::class.java), any(Handler::class.java))
        val timer = LiveScreenSchedule(context) {}
        assertFalse(timer.arm(Instant.now().plusSeconds(60)))
        assertFalse(timer.available)
        timer.close()
    }
    @Test fun fullScreenFilteringMakesTimingAccessOptionalAndDisconnectRestoresFallbackRules() {
        ShadowSettings.setCanDrawOverlays(true)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val controller = Robolectric.buildService(ScreenOverlayService::class.java).create()
        try {
            val service = controller.get()
            ScreenOverlayService::class.java.getDeclaredMethod("onServiceConnected").apply {
                isAccessible = true; invoke(service)
            }
            val config = SleepConfig(screen = true, mode = ScheduleMode.CUSTOM)
            assertTrue(SleepScheduler.timingReady(service, config))
            assertTrue(ScreenOverlayService.hasLiveSchedule())
            service.onUnbind(null)
            assertFalse(ScreenOverlayService.hasLiveSchedule())
            assertFalse(SleepScheduler.timingReady(service, config))
        } finally { controller.destroy() }
    }
    @Test fun futureScheduleUsesListenerInsteadOfPersistentAlarmWithoutAccess() {
        val app = RuntimeEnvironment.getApplication()
        org.robolectric.Shadows.shadowOf(app).setSystemService(Context.ALARM_SERVICE, manager)
        val controller = Robolectric.buildService(ScreenOverlayService::class.java).create()
        try {
            val service = controller.get()
            ScreenOverlayService::class.java.getDeclaredMethod("onServiceConnected").apply {
                isAccessible = true; invoke(service)
            }
            val minute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            SleepSettings.write(service, SleepConfig(screen = true, mode = ScheduleMode.CUSTOM,
                start = (minute + 60) % 1440, end = (minute + 120) % 1440))
            SleepScheduler.reconcile(service)
            verify(manager).setExact(eq(AlarmManager.RTC_WAKEUP), anyLong(), anyString(),
                any(AlarmManager.OnAlarmListener::class.java), any(Handler::class.java))
            verify(manager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(),
                any(android.app.PendingIntent::class.java))
        } finally { controller.destroy() }
    }

    @Test fun wakingReevaluatesAnActiveWindowWithoutAlarmOrApplicationOverlayAccess() {
        val app = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(false)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val controller = Robolectric.buildService(ScreenOverlayService::class.java).create()
        try {
            val service = controller.get()
            ScreenOverlayService::class.java.getDeclaredMethod("onServiceConnected").apply {
                isAccessible = true; invoke(service)
            }
            val minute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            SleepSettings.write(service, SleepConfig(screen = true, mode = ScheduleMode.CUSTOM,
                start = (minute + 1439) % 1440, end = (minute + 1) % 1440))
            app.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_SCREEN_ON))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(SleepScheduler.ready(service))
            assertTrue(ScreenOverlayService.hasLiveSchedule())
            val start = org.robolectric.Shadows.shadowOf(app).nextStartedService
            assertNotNull(start)
            assertEquals(com.granularvolume.service.VolumeControlService::class.java.name, start.component!!.className)
        } finally { controller.destroy() }
    }

    @Test fun cancelledCallbackCannotConsumeAReplacementTimer() {
        var calls = 0
        val timer = LiveScreenSchedule(context) { calls++ }
        timer.arm(Instant.now().minusSeconds(1))
        val future = Instant.now().plusSeconds(3600)
        timer.arm(future)
        val listener = ArgumentCaptor.forClass(AlarmManager.OnAlarmListener::class.java)
        verify(manager, times(2)).setExact(anyInt(), anyLong(), anyString(), listener.capture(), any(Handler::class.java))
        listener.value.onAlarm() // Delivery of the first alarm was already queued at cancellation.
        assertEquals(0, calls)
        timer.arm(future)
        verify(manager, times(2)).setExact(anyInt(), anyLong(), anyString(), any(AlarmManager.OnAlarmListener::class.java), any(Handler::class.java))
        timer.close()
        listener.value.onAlarm()
        assertEquals(0, calls)
    }

}
