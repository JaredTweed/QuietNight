package dev.jaredtweed.quietnight.sleep

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.time.Instant

/** A component-owned timer, used only while Android keeps the accessibility service bound. */
internal class LiveScreenSchedule(context: Context, private val changed: () -> Unit) {
    private val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val handler = Handler(Looper.getMainLooper())
    private var next: Instant? = null
    private var closed = false
    private var failed = false
    private val listener = AlarmManager.OnAlarmListener {
        val boundary = next
        // An old callback may already be queued when a schedule is replaced or cancelled.
        if (!closed && boundary != null && Instant.now().toEpochMilli() >= boundary.toEpochMilli()) {
            next = null
            changed()
        }
    }

    // Android 12/13 required exact-alarm access even for listener alarms. Android 14+
    // explicitly exempts this overload, but cancels it when its owning component stops.
    val available: Boolean get() = !closed && !failed &&
        (Build.VERSION.SDK_INT < 31 || Build.VERSION.SDK_INT >= 34)

    fun arm(boundary: Instant?): Boolean {
        if (!available) return false
        if (next == boundary) return true
        cancel()
        if (boundary == null) return true
        return try {
            manager.setExact(AlarmManager.RTC_WAKEUP, boundary.toEpochMilli(),
                "Quiet Night screen schedule", listener, handler)
            next = boundary
            true
        } catch (_: SecurityException) {
            failed = true
            false
        }
    }

    fun cancel() {
        next = null
        manager.cancel(listener)
        // Let AlarmManager's queued wrapper finish so it can acknowledge delivery and
        // release its wake lock. The listener above makes stale delivery harmless.
    }

    fun close() { closed = true; cancel() }
}
