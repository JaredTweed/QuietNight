package dev.jaredtweed.quietnight.sleep

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import dev.jaredtweed.quietnight.audio.FullRangeCoordinator
import dev.jaredtweed.quietnight.service.VolumeControlService
import dev.jaredtweed.quietnight.util.ControlLive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenOverlayKeyTest {
    @Before fun setup() {
        ControlLive.reset()
        SleepSettings.write(RuntimeEnvironment.getApplication(), SleepConfig(audio = true))
        val audio = mock(AudioManager::class.java)
        `when`(audio.mode).thenReturn(AudioManager.MODE_NORMAL)
        shadowOf(RuntimeEnvironment.getApplication()).setSystemService(Context.AUDIO_SERVICE, audio)
    }
    private fun connect(service: ScreenOverlayService) {
        ScreenOverlayService::class.java.getDeclaredMethod("onServiceConnected").apply {
            isAccessible = true; invoke(service)
        }
        val app = shadowOf(RuntimeEnvironment.getApplication())
        while (app.nextStartedService != null) { }
        ControlLive.running = true
        ControlLive.audioState = FullRangeCoordinator.UiState(false, true, -15f, 1, 15, 10, false)
    }
    private fun key(service: ScreenOverlayService, event: KeyEvent): Boolean =
        ScreenOverlayService::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java).apply {
            isAccessible = true
        }.invoke(service, event) as Boolean

    private fun flags(service: ScreenOverlayService): Int =
        ReflectionHelpers.getField<AccessibilityServiceInfo>(service, "mInfo").flags

    @Test fun keyHandlingIsRequestedOnlyWhileQuietStepsAreEnabledAndNeverRequestsABubble() {
        val controller = Robolectric.buildService(ScreenOverlayService::class.java).create()
        try {
            val service = controller.get(); connect(service)
            assertTrue(flags(service) and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0)
            assertEquals(0, flags(service) and AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON)
            SleepSettings.update(service) { it.copy(audio = false) }
            assertEquals(0, flags(service) and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS)
        } finally { controller.destroy(); ControlLive.reset() }
    }
    @Test fun holdingDispatchesAnExplicitMuteCommandWithoutConsumingThePhysicalKey() {
        val controller = Robolectric.buildService(ScreenOverlayService::class.java).create()
        try {
            val service = controller.get(); connect(service)
            val now = SystemClock.uptimeMillis()
            assertFalse(key(service, KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0)))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            val command = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
            assertNotNull(command)
            assertEquals(VolumeControlService.ACTION_MUTE_MEDIA, command.action)
        } finally { controller.destroy(); ControlLive.reset() }
    }
    @Test fun turningQuietStepsOffCancelsAnAlreadyHeldKey() {
        val controller = Robolectric.buildService(ScreenOverlayService::class.java).create()
        try {
            val service = controller.get(); connect(service)
            val now = SystemClock.uptimeMillis()
            key(service, KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0))
            SleepSettings.update(service) { it.copy(audio = false) }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        } finally { controller.destroy(); ControlLive.reset() }
    }
}
