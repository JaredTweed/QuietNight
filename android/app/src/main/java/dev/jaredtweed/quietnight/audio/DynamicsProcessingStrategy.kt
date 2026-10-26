package dev.jaredtweed.quietnight.audio

import android.media.audiofx.DynamicsProcessing

/** Flat, all-channel attenuation on the global output mix (API 28+). */
class DynamicsProcessingStrategy : AudioEffectStrategy {
    private val handle = AudioEffectHandle<DynamicsProcessing>("QuietNight:DynProc")

    override fun initialize(): Boolean = handle.initialize({
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // Stereo output mix.
            false, 0, false, 0, false, 0, false
        ).build()
        DynamicsProcessing(AudioEffectStrategy.EFFECT_PRIORITY, AudioEffectStrategy.GLOBAL_SESSION_ID, config)
    })

    // DynamicsProcessing exposes input gain rather than a global output-gain setter.
    override fun setAttenuation(dB: Float): Boolean =
        handle.apply { it.setInputGainAllChannelsTo(dB) }

    override fun release() = handle.release()
}
