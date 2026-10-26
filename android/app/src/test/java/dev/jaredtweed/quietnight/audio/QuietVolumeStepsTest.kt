package dev.jaredtweed.quietnight.audio

import org.junit.Assert.*
import org.junit.Test

class QuietVolumeStepsTest {
    @Test fun coalescedRisesEqualSequentialPressesAcrossEveryQuietStepAndFloor() {
        val maximum = 15
        for (floor in 1..5) for (depth in 1..6) for (presses in 1..(maximum - floor)) {
            var gain = -depth * 5f
            var hardware = floor
            repeat(presses) {
                if (gain < 0f) {
                    val next = QuietVolumeSteps.rise(gain, hardware, hardware + 1, floor, maximum)!!
                    gain = next.gainDb; hardware = next.hardwareIndex
                } else hardware++
            }
            val batch = QuietVolumeSteps.rise(-depth * 5f, floor, floor + presses, floor, maximum)!!
            assertEquals(gain, batch.gainDb)
            assertEquals(hardware, batch.hardwareIndex)
        }
    }
    @Test fun calibratedHeadsetUnmuteConsumesOnlyTheFirstPress() {
        val result = QuietVolumeSteps.rise(-30f, 0, 4, 5, 15)!!
        assertEquals(-15f, result.gainDb)
        assertEquals(5, result.hardwareIndex)
        val speaker = QuietVolumeSteps.rise(-30f, 0, 4, 1, 15)!!
        assertEquals(-10f, speaker.gainDb)
    }
    @Test fun alreadyReconciledOrDownwardSnapshotsCannotReplayAnUpwardBurst() {
        assertNull(QuietVolumeSteps.rise(-15f, 2, 2, 2, 15))
        assertNull(QuietVolumeSteps.rise(-15f, 2, 1, 2, 15))
    }
    @Test fun downwardBurstReachesThirtyDbBeforeTheNextStepMutes() {
        val floor = QuietVolumeSteps.fall(-20f, 2, 5)
        assertEquals(-30f, floor.gainDb); assertEquals(5, floor.hardwareIndex)
        val muted = QuietVolumeSteps.fall(-20f, 3, 5)
        assertEquals(-30f, muted.gainDb); assertEquals(0, muted.hardwareIndex)
    }
    @Test fun surplusHardwareStepsCannotExceedTheStreamMaximum() {
        assertEquals(15, QuietVolumeSteps.rise(-5f, 0, 15, 5, 15)!!.hardwareIndex)
    }
}
