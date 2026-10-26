package dev.jaredtweed.quietnight.audio

import android.media.audiofx.LoudnessEnhancer

/** Best-effort fallback; support for negative millibel gain depends on the device. */
class LoudnessEnhancerStrategy : AudioEffectStrategy {
    private val handle = AudioEffectHandle<LoudnessEnhancer>("QuietNight:LE")

    override fun initialize(): Boolean = handle.initialize(
        { LoudnessEnhancer(AudioEffectStrategy.GLOBAL_SESSION_ID) },
        { it.setTargetGain(0) }
    )

    override fun setAttenuation(dB: Float): Boolean =
        handle.apply { it.setTargetGain((dB * MILLIBELS_PER_DB).toInt()) }

    override fun release() = handle.release()

    private companion object { const val MILLIBELS_PER_DB = 100 }
}
