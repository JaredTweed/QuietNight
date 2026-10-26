package dev.jaredtweed.quietnight.audio

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VolumeDownHoldDetectorTest {
    private var enabled = true
    private var calls = 0
    private lateinit var hold: VolumeDownHoldDetector
    private var down = 0L
    @Before fun setup() {
        hold = VolumeDownHoldDetector({ enabled }, { calls++ })
        down = SystemClock.uptimeMillis()
    }
    private fun event(action: Int, key: Int = KeyEvent.KEYCODE_VOLUME_DOWN, repeat: Int = 0,
                      start: Long = down, flags: Int = 0): KeyEvent =
        KeyEvent(start, SystemClock.uptimeMillis(), action, key, repeat, 0, 1, 0, flags)
    private fun waitFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun firesAtTwoSecondsWhileHeldAndNeverConsumesTheKey() {
        assertFalse(hold.onKeyEvent(event(KeyEvent.ACTION_DOWN)))
        waitFor(1999); assertEquals(0, calls)
        waitFor(1); assertEquals(1, calls)
        repeat(20) { assertFalse(hold.onKeyEvent(event(KeyEvent.ACTION_DOWN, repeat = it + 1))) }
        waitFor(3000); assertEquals(1, calls)
        assertFalse(hold.onKeyEvent(event(KeyEvent.ACTION_UP)))
    }
    @Test fun shortPressesAndRepeatedClicksNeverAddUpToALongHold() {
        repeat(5) {
            down = SystemClock.uptimeMillis()
            hold.onKeyEvent(event(KeyEvent.ACTION_DOWN))
            waitFor(500)
            hold.onKeyEvent(event(KeyEvent.ACTION_UP))
        }
        waitFor(3000); assertEquals(0, calls)
    }
    @Test fun repeatEventsCannotRestartTheDeadline() {
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN))
        repeat(3) { waitFor(500); hold.onKeyEvent(event(KeyEvent.ACTION_DOWN, repeat = it + 1)) }
        waitFor(500); assertEquals(1, calls)
        hold.cancel()
    }
    @Test fun volumeUpAndDisconnectionCancelThePendingHold() {
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN)); waitFor(1000)
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP))
        waitFor(2000); assertEquals(0, calls)
        down = SystemClock.uptimeMillis()
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN)); hold.cancel()
        waitFor(3000); assertEquals(0, calls)
    }
    @Test fun turningQuietStepsOffOrStartingACallCannotMuteLater() {
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN)); waitFor(1000)
        enabled = false
        waitFor(2000); assertEquals(0, calls)
    }
    @Test fun otherKeysAreIgnoredWithoutEvenQueryingAudioState() {
        val isolated = VolumeDownHoldDetector({ error("Non-volume key must be ignored immediately") }, { error("Must not mute") })
        assertFalse(isolated.onKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
        assertFalse(isolated.onKeyEvent(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)))
    }
    @Test fun enablingMidRepeatDoesNotInventANewPress() {
        enabled = false
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN))
        enabled = true
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN, repeat = 2))
        waitFor(3000); assertEquals(0, calls)
    }
    @Test fun cancelledKeyReleaseCannotMute() {
        hold.onKeyEvent(event(KeyEvent.ACTION_DOWN)); waitFor(1000)
        hold.onKeyEvent(event(KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED))
        waitFor(3000); assertEquals(0, calls)
    }
}
