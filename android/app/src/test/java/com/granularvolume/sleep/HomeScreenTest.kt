package com.granularvolume.sleep

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.materialswitch.MaterialSwitch
import com.granularvolume.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import com.granularvolume.util.ControlLive
import com.granularvolume.util.Prefs
import com.granularvolume.audio.FullRangeCoordinator
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
    @Before fun clearLiveState() { ControlLive.reset() }
    @After fun releaseLiveState() { ControlLive.reset() }

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun screenshots(activity: MainActivity, name: String, schedule: Boolean = false, audio: Boolean = false) {
        val decor = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        decor.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        shadowOf(Looper.getMainLooper()).idle()
        if (schedule) {
            val dial = views(decor).filterIsInstance<SleepWindowDial>().single()
            val scroll = views(decor).filterIsInstance<ScrollView>().single()
            val rect = android.graphics.Rect(); dial.getDrawingRect(rect)
            scroll.offsetDescendantRectToMyCoords(dial, rect); scroll.scrollTo(0, rect.top - 60)
        }
        if (audio) {
            val indicator = views(decor).filterIsInstance<QuietLevelIndicator>().single()
            val scroll = views(decor).filterIsInstance<ScrollView>().single()
            val rect = android.graphics.Rect(); indicator.getDrawingRect(rect)
            scroll.offsetDescendantRectToMyCoords(indicator, rect)
            scroll.scrollTo(0, rect.top - 130)
        }
        val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val file = File("build/reports/screenshots/$name.png"); file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun MainActivity.action(name: String) =
        views(window.decorView).filterIsInstance<ExtendedFloatingActionButton>().single { it.text.startsWith(name) }
    @Test fun homeShowsTwoIndependentBottomRightControlsAndPreciseSchedule() {
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
            assertTrue(initial.filterIsInstance<Slider>().none { it.contentDescription == "Extra attenuation" })
            assertEquals(2, initial.filterIsInstance<ExtendedFloatingActionButton>().size)
            assertTrue(initial.filterIsInstance<Button>().none { it.text.toString() == "Begin wind-down" })
            screenshots(activity, "quiet-night-home")
            initial.filterIsInstance<Button>().single { it.text.toString() == "Custom" }.performClick()
            val custom = views(activity.window.decorView)
            assertEquals(ScheduleMode.CUSTOM, SleepSettings.read(activity).mode)
            assertEquals(1, custom.filterIsInstance<SleepWindowDial>().size)
            assertEquals(2, custom.filterIsInstance<Button>().count { it.text.startsWith("START\n") || it.text.startsWith("END\n") })
            screenshots(activity, "quiet-night-schedule", true)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun screenAndAudioControlsAreIndependentAndTheIndicatorReplacesTheSlider() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.prefs(app).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.action("Screen").performClick()
            var config = SleepSettings.read(activity)
            assertTrue(config.screen); assertFalse(config.audio)
            activity.action("Audio").performClick()
            config = SleepSettings.read(activity)
            assertTrue(config.screen); assertTrue(config.audio)
            val indicator = views(activity.window.decorView).filterIsInstance<QuietLevelIndicator>().single()
            views(activity.window.decorView).filterIsInstance<ScrollView>().single()
                .scrollTo(0, (indicator.parent as View).top)
            screenshots(activity, "quiet-night-audio")
            activity.action("Screen").performClick()
            config = SleepSettings.read(activity)
            assertFalse(config.screen); assertTrue(config.audio)
            activity.action("Audio").performClick()
            assertFalse(SleepSettings.read(activity).audio)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun indicatorReflectsLiveQuietStateWithoutOfferingAnAttenuationSlider() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.write(app, SleepConfig(audio = true))
        Prefs.setAttenuation(app, -15f); Prefs.setLevelInQuietZone(app, true)
        ControlLive.running = true
        ControlLive.effect = ControlLive.Effect.PREFERRED
        ControlLive.audioState = FullRangeCoordinator.UiState(false, true, -15f, 14, 15, 13, false)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val all = views(activity.window.decorView)
            val indicator = all.filterIsInstance<QuietLevelIndicator>().single()
            assertEquals("Quiet Dial, −15 dB", indicator.contentDescription)
            assertFalse(indicator.isClickable)
            assertEquals(2, all.filterIsInstance<Slider>().size)
            screenshots(activity, "quiet-night-audio", audio = true)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun volumeDownIsASeparateOptInBelowTheAudioControl() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.prefs(app).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val down = views(activity.window.decorView).filterIsInstance<MaterialSwitch>()
                .single { it.contentDescription == "Quiet steps with volume-down" }
            assertFalse(down.isEnabled); assertNotNull(down.thumbDrawable); assertNotNull(down.trackDrawable)
            activity.action("Audio").performClick()
            val updated = views(activity.window.decorView).filterIsInstance<MaterialSwitch>()
                .single { it.contentDescription == "Quiet steps with volume-down" }
            assertTrue(updated.isEnabled); updated.performClick()
            assertTrue(SleepSettings.read(activity).volumeDown)
        } finally { controller.pause().stop().destroy() }
    }
}
