package com.granularvolume.util

import com.granularvolume.audio.FullRangeCoordinator

/** Live service state; never inferred from saved activation preferences. */
object ControlLive {
    @Volatile var running = false
    enum class Effect { UNKNOWN, PREFERRED, FALLBACK, NONE }
    @Volatile var effect = Effect.UNKNOWN
    @Volatile var audioState: FullRangeCoordinator.UiState? = null
    fun reset() {
        running = false; effect = Effect.UNKNOWN; audioState = null
    }
}
