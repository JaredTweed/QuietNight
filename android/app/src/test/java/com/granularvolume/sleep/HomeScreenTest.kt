package com.granularvolume.sleep

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.slider.Slider
import com.granularvolume.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun screenshot(activity: MainActivity, name: String, schedule: Boolean = false) {
        val decor = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        decor.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        shadowOf(Looper.getMainLooper()).idle()
        if (schedule) {
            val dial = views(decor).filterIsInstance<SleepWindowDial>().single()
            val scroll = views(decor).filterIsInstance<ScrollView>().single()
            scroll.scrollTo(0, (dial.parent as View).top)
        }
        val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val file = File("build/reports/screenshots/$name.png"); file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun homeAndCustomScheduleRenderWithAccessiblePreciseTimeButtons() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.prefs(app).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val initial = views(activity.window.decorView)
            assertTrue(initial.filterIsInstance<TextView>().any { it.text.toString() == "Make room\nfor rest." })
            assertTrue(initial.filterIsInstance<Slider>().any { it.valueTo == 85f })
            assertTrue(initial.filterIsInstance<Slider>().any { it.valueTo == 100f })
            screenshot(activity, "quiet-night-home")
            initial.filterIsInstance<Button>().single { it.text.toString() == "Custom" }.performClick()
            val custom = views(activity.window.decorView)
            assertEquals(ScheduleMode.CUSTOM, SleepSettings.read(activity).mode)
            assertEquals(1, custom.filterIsInstance<SleepWindowDial>().size)
            assertEquals(2, custom.filterIsInstance<Button>().count { it.text.startsWith("START\n") || it.text.startsWith("END\n") })
            screenshot(activity, "quiet-night-schedule", true)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun quietModuleAndVolumeDownAreSeparateOptInSwitches() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.prefs(app).edit().clear().commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val switches = views(activity.window.decorView).filterIsInstance<SwitchMaterial>()
            val quiet = switches.single { it.contentDescription == "Quiet Dial" }
            val down = switches.single { it.contentDescription == "Quiet steps with volume-down" }
            assertFalse(quiet.isChecked); assertFalse(down.isEnabled)
            assertNotNull(quiet.thumbDrawable); assertNotNull(quiet.trackDrawable)
            assertTrue(quiet.width >= 48 * activity.resources.displayMetrics.density)
            quiet.performClick()
            assertTrue(SleepSettings.read(activity).quiet)
            val updated = views(activity.window.decorView).filterIsInstance<SwitchMaterial>()
                .single { it.contentDescription == "Quiet steps with volume-down" }
            assertTrue(updated.isEnabled); updated.performClick()
            assertTrue(SleepSettings.read(activity).volumeDown)
        } finally { controller.pause().stop().destroy() }
    }
}
