package com.granularvolume.util

import com.granularvolume.audio.FullRangeCoordinator
import com.granularvolume.sleep.ScreenDimming

/** Live service state; never inferred from saved activation preferences. */
object ControlLive {
    @Volatile var running = false
    enum class Effect { UNKNOWN, PREFERRED, FALLBACK, NONE }
    @Volatile var effect = Effect.UNKNOWN
    @Volatile var dimming = ScreenDimming.Mode.OFF
    @Volatile var audioState: FullRangeCoordinator.UiState? = null
    fun reset() {
        running = false; effect = Effect.UNKNOWN
        dimming = ScreenDimming.Mode.OFF; audioState = null
    }
}
