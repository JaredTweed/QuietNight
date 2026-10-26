package com.granularvolume.audio

import android.media.audiofx.AudioEffect
import android.util.Log

/** Owns one native effect, including partially initialized and failed instances. */
internal class AudioEffectHandle<E : AudioEffect>(private val tag: String) {
    private var effect: E? = null

    fun initialize(create: () -> E, configure: (E) -> Unit = {}): Boolean {
        release()
        var candidate: E? = null
        return try {
            candidate = create()
            configure(candidate)
            check(candidate.setEnabled(true) == AudioEffect.SUCCESS) { "Effect could not be enabled" }
            effect = candidate
            true
        } catch (failure: RuntimeException) {
            // A constructor may succeed before configuration or enabling fails.
            candidate?.let { dispose(it) }
            Log.w(tag, "Audio effect initialization failed", failure)
            false
        }
    }

    fun apply(change: (E) -> Unit): Boolean {
        val current = effect ?: return false
        return try {
            change(current)
            true
        } catch (failure: RuntimeException) {
            Log.w(tag, "Audio effect update failed", failure)
            false
        }
    }

    fun release() {
        val current = effect ?: return
        effect = null
        dispose(current)
    }

    private fun dispose(current: E) {
        // Disabling can fail when the audio server dies. Still release the native handle.
        try { current.setEnabled(false) }
        catch (failure: RuntimeException) { Log.w(tag, "Audio effect disable failed", failure) }
        try { current.release() }
        catch (failure: RuntimeException) { Log.w(tag, "Audio effect release failed", failure) }
    }
}
