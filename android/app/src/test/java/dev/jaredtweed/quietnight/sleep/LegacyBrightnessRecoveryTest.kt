package dev.jaredtweed.quietnight.sleep

import android.content.Context
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LegacyBrightnessRecoveryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver
    @Before fun setup() {
        context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE).edit().clear().commit()
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 50)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
    }
    private fun record(phase: String = "active") {
        context.getSharedPreferences("quiet_night_display", Context.MODE_PRIVATE).edit()
            .putInt("original", 100).putInt("mode", 1).putInt("target", 50)
            .putString("phase", phase).commit()
    }
    @Test fun ownedLegacySessionRestoresOnceAndNewDimmingNeverCreatesARecord() {
        record()
        assertTrue(LegacyBrightnessRecovery.restore(context))
        assertEquals(100, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(1, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
        assertFalse(LegacyBrightnessRecovery.pending(context))
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 80)
        assertTrue(LegacyBrightnessRecovery.restore(context))
        assertEquals(80, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
    }
    @Test fun userBrightnessChangeWins() {
        record()
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 75)
        assertTrue(LegacyBrightnessRecovery.restore(context))
        assertEquals(75, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
        assertFalse(LegacyBrightnessRecovery.pending(context))
    }
    @Test fun interruptedRestoreFinishesAdaptiveMode() {
        record("restoring")
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 100)
        assertTrue(LegacyBrightnessRecovery.restore(context))
        assertEquals(1, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
    }
}
