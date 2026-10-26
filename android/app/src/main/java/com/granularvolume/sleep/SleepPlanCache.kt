package com.granularvolume.sleep

import java.time.Instant
import java.time.ZoneId

/** Reuse a plan until its next boundary, a clock rollback, a zone change, or a settings edit. */
internal class SleepPlanCache(
    private val calculate: (SleepConfig, Instant, ZoneId) -> SleepPlan = SleepSchedule::plan
) {
    private var config: SleepConfig? = null
    private var zone: ZoneId? = null
    private var calculatedAt: Instant? = null
    private var cached: SleepPlan? = null

    fun plan(config: SleepConfig, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): SleepPlan {
        val previous = cached
        if (previous != null && this.config == config && this.zone == zone &&
            calculatedAt?.let { now >= it } == true && (previous.nextChange == null || now < previous.nextChange)) return previous
        return calculate(config, now, zone).also {
            this.config = config
            this.zone = zone
            calculatedAt = now
            cached = it
        }
    }
}
