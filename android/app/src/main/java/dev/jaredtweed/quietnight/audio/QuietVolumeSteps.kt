package dev.jaredtweed.quietnight.audio

import dev.jaredtweed.quietnight.util.Prefs
import kotlin.math.ceil

/** Pure mapping from observable hardware movement to the quiet ladder and remaining upper steps. */
internal object QuietVolumeSteps {
    data class Adjustment(val gainDb: Float, val hardwareIndex: Int) {
        val quiet: Boolean get() = gainDb < 0f
    }

    fun rise(gainDb: Float, anchor: Int, observed: Int, floor: Int, maximum: Int): Adjustment? {
        if (observed <= anchor) return null
        // A calibrated headset needs one press to restore audibility from a silent index.
        val unmuteStep = if (anchor < floor && floor > 1) 1 else 0
        val steps = (observed - anchor - unmuteStep).coerceAtLeast(0)
        val quietStepsLeft = ceil(-gainDb / VolumeCurve.RUNG_DB).toInt().coerceAtLeast(0)
        val gain = (gainDb + steps * VolumeCurve.RUNG_DB).coerceAtMost(0f)
        val upperSteps = (steps - quietStepsLeft).coerceAtLeast(0)
        return Adjustment(gain, (floor + upperSteps).coerceAtMost(maximum))
    }

    fun fall(gainDb: Float, steps: Int, floor: Int): Adjustment {
        val quietStepsLeft = ceil((gainDb - Prefs.ATTENUATION_MIN) / VolumeCurve.RUNG_DB).toInt()
        val gain = (gainDb - steps * VolumeCurve.RUNG_DB).coerceAtLeast(Prefs.ATTENUATION_MIN)
        return Adjustment(gain, if (steps > quietStepsLeft) 0 else floor)
    }
}
