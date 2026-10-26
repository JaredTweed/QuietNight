package com.granularvolume.audio

import android.media.audiofx.AudioEffect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioEffectHandleTest {
    @Test fun configurationFailureReleasesTheConstructedNativeEffect() {
        val effect = mock(AudioEffect::class.java)
        val handle = AudioEffectHandle<AudioEffect>("test")
        assertFalse(handle.initialize({ effect }, { error("Configuration rejected") }))
        verify(effect).release()
        assertFalse(handle.apply { error("Must not retain failed effect") })
        handle.release()
        verify(effect, times(1)).release()
    }
    @Test fun failedEnableStatusIsNotMistakenForSuccessfulInitialization() {
        val effect = mock(AudioEffect::class.java)
        `when`(effect.setEnabled(true)).thenReturn(AudioEffect.ERROR_DEAD_OBJECT)
        val handle = AudioEffectHandle<AudioEffect>("test")
        assertFalse(handle.initialize({ effect }))
        verify(effect).release()
    }
    @Test fun disableFailureCannotSkipReleaseAndRepeatedReleaseIsHarmless() {
        val effect = mock(AudioEffect::class.java)
        `when`(effect.setEnabled(true)).thenReturn(AudioEffect.SUCCESS)
        `when`(effect.setEnabled(false)).thenThrow(IllegalStateException("Audio server died"))
        val handle = AudioEffectHandle<AudioEffect>("test")
        assertTrue(handle.initialize({ effect }))
        handle.release(); handle.release()
        verify(effect, times(1)).release()
    }
}
