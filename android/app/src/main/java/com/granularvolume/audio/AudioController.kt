package com.granularvolume.audio

import android.content.Context
import android.util.Log
import com.granularvolume.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Serializes native-effect ownership, requested gain, and persisted audio state. */
class AudioController internal constructor(
    context: Context,
    private val strategyFactories: List<() -> AudioEffectStrategy>
) {
    constructor(context: Context) : this(context, listOf(::DynamicsProcessingStrategy, ::LoudnessEnhancerStrategy))

    private val context = context.applicationContext
    private val tag = "GranularVolume:AudioCtrl"
    @Volatile private var strategy: AudioEffectStrategy? = null
    private var released = false
    val lockedFloorDb = 0f

    /** Optional policy hook retained for callers; Quiet Night always uses the open default. */
    var proProvider: () -> Boolean = { true }
    fun keepsLevel(): Boolean = proProvider()

    private val _attenuationDb = MutableStateFlow(Prefs.getAttenuation(this.context))
    val attenuationDb: StateFlow<Float> = _attenuationDb.asStateFlow()

    @Volatile var isEffectAvailable = false
        private set
    var onEffectState: ((available: Boolean, preferred: Boolean) -> Unit)? = null
    val usingPreferredStrategy: Boolean
        get() = isEffectAvailable && strategy is DynamicsProcessingStrategy

    /** Idempotent initialization. A failed candidate is released before trying the fallback. */
    @Synchronized
    fun initialize(): Boolean {
        if (released) return false
        if (isEffectAvailable) return true
        releaseStrategy()
        val gain = if (proProvider()) _attenuationDb.value else _attenuationDb.value.coerceAtLeast(lockedFloorDb)
        for (create in strategyFactories) {
            var candidate: AudioEffectStrategy? = null
            val initialized = try {
                candidate = create()
                candidate.initialize() && candidate.setAttenuation(gain)
            } catch (failure: RuntimeException) {
                Log.w(tag, "Audio effect initialization failed", failure)
                false
            }
            if (initialized) {
                strategy = candidate
                _attenuationDb.value = gain
                if (proProvider()) Prefs.setAttenuation(context, gain)
                isEffectAvailable = true
                onEffectState?.invoke(true, usingPreferredStrategy)
                return true
            }
            candidate?.let { releaseSafely(it) }
        }
        onEffectState?.invoke(false, false)
        return false
    }

    enum class GainSource { QUIET_STEP, CURVE_REMAINDER, SYSTEM, MUTE }

    /** Invalid requests and calls after release cannot alter the effect or saved level. */
    @Synchronized
    fun setAttenuation(dB: Float, source: GainSource = GainSource.SYSTEM) {
        if (released || !dB.isFinite()) return
        val requested = dB.coerceIn(Prefs.ATTENUATION_MIN, Prefs.ATTENUATION_MAX)
        val clamped = if (proProvider()) requested else requested.coerceAtLeast(lockedFloorDb)
        val current = strategy
        if (current != null) {
            val available = try { current.setAttenuation(clamped) }
            catch (failure: RuntimeException) {
                Log.w(tag, "Audio effect update failed", failure)
                false
            }
            if (available != isEffectAvailable) {
                isEffectAvailable = available
                onEffectState?.invoke(available, usingPreferredStrategy)
            }
        }
        // Keep the desired gain when the audio server fails so reattach can restore it.
        _attenuationDb.value = clamped
        if (proProvider()) Prefs.setAttenuation(context, clamped)
        Log.d(tag, "Attenuation set to ${clamped}dB (source=$source)")
    }

    fun mute() = setAttenuation(Prefs.ATTENUATION_MIN, GainSource.MUTE)
    fun passThrough() = setAttenuation(Prefs.ATTENUATION_MAX)

    /** Rebuild after routing or audio-mode changes, preserving the desired gain. */
    @Synchronized
    fun reattach() {
        if (released) return
        releaseStrategy()
        initialize()
    }

    /** Idempotent, terminal teardown; late callbacks cannot recreate an effect. */
    @Synchronized
    fun release() {
        if (released) return
        released = true
        releaseStrategy()
        onEffectState = null
    }

    private fun releaseStrategy() {
        val current = strategy
        strategy = null
        isEffectAvailable = false
        current?.let { releaseSafely(it) }
    }

    private fun releaseSafely(effect: AudioEffectStrategy) {
        try { effect.release() }
        catch (failure: RuntimeException) { Log.w(tag, "Audio effect release failed", failure) }
    }
}
