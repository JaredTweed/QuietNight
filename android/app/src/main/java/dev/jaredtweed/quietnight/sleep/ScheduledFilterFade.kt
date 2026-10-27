package dev.jaredtweed.quietnight.sleep

import java.time.Duration
import java.time.Instant

/** A fade anchored to the actual schedule, independent of app/service restarts. */
internal data class ScheduledFilterFade(val start: Instant, val end: Instant, val durationMillis: Long) {
    fun finishedWindow(now: Instant): Boolean = now >= end

    fun opacityStep(now: Instant): Int {
        val elapsed = Duration.between(start, now).toMillis().coerceIn(0, durationMillis)
        return (elapsed * STEPS / durationMillis).toInt()
    }

    fun nextUpdateDelay(now: Instant): Long? {
        if (finishedWindow(now)) return null
        val step = opacityStep(now)
        if (step == STEPS) return null
        // Only schedule the next visible 8-bit opacity change, rather than running a
        // frame-rate animation for up to an hour. Ceiling division avoids early ticks.
        val nextMillis = ((step + 1L) * durationMillis + STEPS - 1) / STEPS
        val next = minOf(start.plusMillis(nextMillis), end)
        return Duration.between(now, next).toMillis().coerceAtLeast(1)
    }

    companion object {
        const val STEPS = 255

        fun from(config: SleepConfig, plan: SleepPlan?): ScheduledFilterFade? {
            val minutes = config.fadeInMinutes.coerceIn(0, SleepConfig.MAX_FADE_IN_MINUTES)
            if (!config.screen || config.mode == ScheduleMode.ALWAYS || minutes == 0 || plan?.active != true)
                return null
            val start = plan.activeSince ?: return null
            val end = plan.nextChange ?: return null
            return ScheduledFilterFade(start, end, minutes * 60_000L)
        }
    }
}
