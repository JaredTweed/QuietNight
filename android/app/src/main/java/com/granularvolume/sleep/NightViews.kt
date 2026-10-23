package com.granularvolume.sleep

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

object NightColors {
    val background = Color.rgb(16, 14, 22)
    val surface = Color.rgb(29, 25, 36)
    val stroke = Color.rgb(54, 45, 62)
    val text = Color.rgb(246, 235, 226)
    val secondary = Color.rgb(183, 170, 189)
    val amber = Color.rgb(246, 189, 136)
    val lavender = Color.rgb(205, 181, 237)
}

/** A quiet illustration drawn locally: no image downloads or animation running overnight. */
class MoonView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val x = width * .62f; val y = height * .45f; val r = min(width, height) * .26f
        p.shader = RadialGradient(x, y, r * 2.2f, intArrayOf(0x48674B68, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 2.2f, p); p.shader = null
        p.color = NightColors.amber; c.drawCircle(x, y, r, p)
        p.color = NightColors.background; c.drawCircle(x + r * .5f, y - r * .3f, r * .93f, p)
        p.color = NightColors.lavender
        for ((sx, sy) in listOf(.2f to .18f, .86f to .23f, .27f to .7f, .83f to .77f)) {
            val a = width * sx; val b = height * sy
            c.drawCircle(a, b, resources.displayMetrics.density * 1.5f, p)
        }
    }
}

/** Independent start/end handles on a 24-hour circle, including intervals across midnight. */
class SleepWindowDial(context: Context) : View(context) {
    var start = 1320
    var end = 420
    var onChange: ((Int, Int) -> Unit)? = null
    private var movingStart = true
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    init { contentDescription = "24-hour schedule. Set exact times with the Start and End buttons below." }
    private fun point(minute: Int): PointF {
        val a = Math.toRadians(minute / 1440.0 * 360 - 90)
        val r = min(width, height) / 2f - 29 * d
        return PointF(width / 2f + cos(a).toFloat() * r, height / 2f + sin(a).toFloat() * r)
    }
    override fun onDraw(c: Canvas) {
        val x = width / 2f; val y = height / 2f; val r = min(width, height) / 2f - 29 * d
        p.style = Paint.Style.STROKE; p.strokeWidth = 13 * d; p.strokeCap = Paint.Cap.ROUND
        p.color = NightColors.stroke; c.drawCircle(x, y, r, p)
        p.shader = SweepGradient(x, y, intArrayOf(NightColors.lavender, NightColors.amber, NightColors.lavender), null)
        val sweep = ((end - start + 1440) % 1440) / 1440f * 360
        c.drawArc(x-r, y-r, x+r, y+r, start / 1440f * 360 - 90, sweep, false, p)
        p.shader = null; p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        p.textSize = 11 * resources.displayMetrics.scaledDensity; p.color = NightColors.secondary
        c.drawText("00", x, y-r-17*d, p); c.drawText("12", x, y+r+24*d, p)
        c.drawText("18", x-r-21*d, y+4*d, p); c.drawText("06", x+r+21*d, y+4*d, p)
        val duration = (end - start + 1440) % 1440
        p.color = NightColors.text; p.textSize = 30 * resources.displayMetrics.scaledDensity
        p.typeface = Typeface.create("serif", Typeface.NORMAL)
        val hours = duration / 60; val mins = duration % 60
        c.drawText(if (mins == 0) "${hours}h" else "${hours}h ${mins}m", x, y, p)
        p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        p.textSize = 12 * resources.displayMetrics.scaledDensity; p.color = NightColors.secondary
        c.drawText("wind-down window", x, y + 24 * d, p)
        for ((minute, color) in listOf(start to NightColors.amber, end to NightColors.lavender)) {
            val at = point(minute)
            p.color = NightColors.background; c.drawCircle(at.x, at.y, 15*d, p)
            p.color = color; c.drawCircle(at.x, at.y, 10*d, p)
            p.color = NightColors.background; c.drawCircle(at.x, at.y, 3*d, p)
        }
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            val radius = min(width, height) / 2f - 29 * d
            val distance = hypot(event.x - width / 2f, event.y - height / 2f)
            // Only the ring captures a drag; the center stays available for page scrolling.
            if (abs(distance - radius) > 28 * d) return false
            val a = point(start); val b = point(end)
            movingStart = hypot(event.x-a.x, event.y-a.y) <= hypot(event.x-b.x, event.y-b.y)
            parent.requestDisallowInterceptTouchEvent(true)
        }
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN || event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
            val angle = (Math.toDegrees(atan2((event.y-height/2f).toDouble(), (event.x-width/2f).toDouble())) + 450) % 360
            val minute = ((angle / 360 * 1440 / 15).roundToInt() * 15) % 1440
            if (movingStart && minute != end) start = minute
            if (!movingStart && minute != start) end = minute
            invalidate(); onChange?.invoke(start, end); return true
        }
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
            parent.requestDisallowInterceptTouchEvent(false); performClick(); return true
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
