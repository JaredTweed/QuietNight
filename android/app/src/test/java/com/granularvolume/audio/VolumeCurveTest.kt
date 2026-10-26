package com.granularvolume.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VolumeCurveTest {
    private lateinit var am: AudioManager
    private var gains = floatArrayOf(Float.NEGATIVE_INFINITY, -30f, -20f, -10f, 0f)
    private fun device(type: Int): AudioDeviceInfo = mock(AudioDeviceInfo::class.java).also {
        `when`(it.type).thenReturn(type)
    }
    @Before fun setup() {
        am = mock(AudioManager::class.java)
        `when`(am.getStreamMaxVolume(anyInt())).thenReturn(4)
        `when`(am.getStreamMinVolume(anyInt())).thenReturn(0)
        `when`(am.getStreamVolumeDb(anyInt(), anyInt(), anyInt())).thenAnswer { gains[it.getArgument<Int>(1)] }
        `when`(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(emptyArray())
        val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        `when`(am.getAudioDevicesForAttributes(any(AudioAttributes::class.java))).thenReturn(listOf(speaker))
    }
    @Test fun mutedIndexMayBeInfiniteButAudibleRungsStayFiniteAndBounded() {
        val curve = VolumeCurve.read(am, AudioManager.STREAM_MUSIC)!!
        assertEquals(-30f, curve.floorDb)
        assertEquals(7, curve.rungs.size)
        curve.rungs.forEach {
            assertTrue(it.remainderDb.isFinite())
            assertTrue(it.remainderDb <= 0f)
            assertTrue(it.hardwareIndex in 1..4)
        }
    }
    @Test fun activeSpeakerIsUsedEvenWhileBluetoothRemainsConnected() {
        val bluetooth = device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        `when`(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(bluetooth))
        assertFalse(VolumeCurve.isWirelessRoute(am))
        assertNotNull(VolumeCurve.read(am, AudioManager.STREAM_MUSIC))
        verify(am).getStreamVolumeDb(AudioManager.STREAM_MUSIC, 1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    }
    @Test fun multipleActiveOutputsDoNotInventASharedCurve() {
        val routes = listOf(device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        `when`(am.getAudioDevicesForAttributes(any(AudioAttributes::class.java))).thenReturn(routes)
        assertTrue(VolumeCurve.isWirelessRoute(am))
        assertNull(VolumeCurve.read(am, AudioManager.STREAM_MUSIC))
    }
    @Test fun invalidReferenceOrAudibleGainIsRejectedRatherThanReplacedWithARealNumber() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (index in 1..4) {
                gains = floatArrayOf(Float.NEGATIVE_INFINITY, -30f, -20f, -10f, 0f)
                gains[index] = invalid
                assertNull(VolumeCurve.read(am, AudioManager.STREAM_MUSIC))
            }
        }
    }
    @Test fun nonMonotoneAndImplausibleCurvesAreRejected() {
        gains[2] = -40f
        assertNull(VolumeCurve.read(am, AudioManager.STREAM_MUSIC))
        gains = floatArrayOf(Float.NEGATIVE_INFINITY, -200f, -150f, -100f, 0f)
        assertNull(VolumeCurve.read(am, AudioManager.STREAM_MUSIC))
    }
    @Test fun calibratedSilentIndicesAreAllowedBelowTheAudibleFloor() {
        gains[1] = Float.NEGATIVE_INFINITY
        val curve = VolumeCurve.read(am, AudioManager.STREAM_MUSIC, 2)!!
        assertEquals(2, curve.minAudibleIndex)
        assertEquals(-20f, curve.floorDb)
    }
    @Test fun routeQueryFailureFallsBackToConnectedOutputs() {
        `when`(am.getAudioDevicesForAttributes(any(AudioAttributes::class.java)))
            .thenThrow(IllegalStateException("Audio service unavailable"))
        val wired = device(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        `when`(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(wired))
        assertNotNull(VolumeCurve.read(am, AudioManager.STREAM_MUSIC))
        verify(am).getStreamVolumeDb(AudioManager.STREAM_MUSIC, 1, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
    }
}
