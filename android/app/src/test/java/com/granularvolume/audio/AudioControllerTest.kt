package com.granularvolume.audio

import android.content.Context
import com.granularvolume.util.Prefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioControllerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private class Effect : AudioEffectStrategy {
        var initializes = 0
        var releases = 0
        var available = true
        var accepted = true
        var failRelease = false
        val gains = mutableListOf<Float>()
        override fun initialize(): Boolean { initializes++; return available }
        override fun setAttenuation(dB: Float): Boolean { gains += dB; return accepted }
        override fun release() { releases++; if (failRelease) error("Audio server died") }
    }
    @Before fun clear() { context.getSharedPreferences("gv_prefs", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun repeatedInitializationOwnsExactlyOneEffect() {
        val effect = Effect()
        val controller = AudioController(context, listOf({ effect }))
        assertTrue(controller.initialize()); assertTrue(controller.initialize())
        assertEquals(1, effect.initializes)
        controller.release(); controller.release()
        assertEquals(1, effect.releases)
        assertFalse(controller.isEffectAvailable)
    }

    @Test fun initialGainFailureReleasesCandidateBeforeTryingFallback() {
        val first = Effect().apply { accepted = false; failRelease = true }
        val fallback = Effect()
        val controller = AudioController(context, listOf({ first }, { fallback }))
        assertTrue(controller.initialize())
        assertEquals(1, first.releases)
        assertEquals(1, fallback.initializes)
        controller.release()
        assertEquals(1, fallback.releases)
    }

    @Test fun failurePublishesUnavailableAndReattachRestoresRequestedGain() {
        val first = Effect()
        val second = Effect()
        var creations = 0
        val controller = AudioController(context, listOf({ if (creations++ == 0) first else second }))
        controller.initialize()
        val states = mutableListOf<Boolean>()
        controller.onEffectState = { available, _ -> states += available }
        first.accepted = false
        controller.setAttenuation(-20f)
        assertFalse(controller.isEffectAvailable)
        assertEquals(listOf(false), states)
        controller.reattach()
        assertTrue(controller.isEffectAvailable)
        assertEquals(listOf(-20f), second.gains)
        assertEquals(-20f, Prefs.getAttenuation(context))
        controller.release()
    }

    @Test fun releaseIsTerminalEvenWhenNativeTeardownThrows() {
        val effect = Effect().apply { failRelease = true }
        val controller = AudioController(context, listOf({ effect }))
        controller.initialize(); controller.setAttenuation(-15f); controller.release()
        controller.setAttenuation(-30f); controller.reattach()
        assertFalse(controller.initialize())
        assertEquals(1, effect.initializes)
        assertEquals(1, effect.releases)
        assertEquals(-15f, Prefs.getAttenuation(context))
        assertEquals(-15f, controller.attenuationDb.value)
    }

    @Test fun nonFiniteRequestsCannotReachNativeEffectOrPreferences() {
        val effect = Effect()
        val controller = AudioController(context, listOf({ effect }))
        controller.initialize(); controller.setAttenuation(-10f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { controller.setAttenuation(it) }
        assertEquals(listOf(0f, -10f), effect.gains)
        assertEquals(-10f, Prefs.getAttenuation(context))
        controller.release()
    }

    @Test fun invalidPersistedGainIsSafeAndValidValuesAreClamped() {
        val p = context.getSharedPreferences("gv_prefs", Context.MODE_PRIVATE)
        p.edit().putFloat("attenuation_db", Float.NaN).commit()
        assertEquals(0f, AudioController(context).attenuationDb.value)
        p.edit().putFloat("attenuation_db", -100f).commit()
        assertEquals(-30f, Prefs.getAttenuation(context))
    }
}
