package dev.jaredtweed.quietnight.sleep

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
import dev.jaredtweed.quietnight.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import dev.jaredtweed.quietnight.util.ControlLive
import dev.jaredtweed.quietnight.util.Prefs
import dev.jaredtweed.quietnight.audio.FullRangeCoordinator
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
        // Let Material's attach/state animations settle before capturing the controls.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
        val decor = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        views(decor).forEach {
            // Bitmap screenshots cannot composite hardware-only animation layers reliably.
            it.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            it.forceLayout()
        }
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
        views(decor).filterIsInstance<ExtendedFloatingActionButton>().forEach { action ->
            val bounds = android.graphics.Rect()
            assertEquals(View.VISIBLE, action.visibility)
            assertEquals(1f, action.alpha, .01f)
            assertTrue("$name: ${action.text} not visible (${action.width} x ${action.height})",
                action.getGlobalVisibleRect(bounds))
        }
        decor.draw(Canvas(bitmap))
        val file = File("build/reports/screenshots/$name.png"); file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun MainActivity.action(name: String) =
        views(window.decorView).filterIsInstance<ExtendedFloatingActionButton>().single { it.contentDescription.toString().startsWith("Turn $name ") }
    @Test fun homeShowsTwoIndependentBottomRightControlsAndPreciseSchedule() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.prefs(app).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val initial = views(activity.window.decorView)
            assertTrue(initial.filterIsInstance<TextView>().any { it.text.toString() == "Quiet Night" })
            assertTrue(initial.filterIsInstance<Slider>().any { it.valueTo == 85f })
            assertTrue(initial.filterIsInstance<Slider>().any { it.valueTo == 100f })
            assertTrue(initial.filterIsInstance<Slider>().none { it.contentDescription == "Extra attenuation" })
            assertEquals(2, initial.filterIsInstance<ExtendedFloatingActionButton>().size)
            assertEquals("Filter off", activity.action("screen filter").text.toString())
            assertEquals("Quiet steps off", activity.action("quiet steps").text.toString())
            assertTrue(initial.filterIsInstance<QuietLevelIndicator>().isEmpty())
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
            activity.action("screen filter").performClick()
            var config = SleepSettings.read(activity)
            assertTrue(config.screen); assertFalse(config.audio)
            activity.action("quiet steps").performClick()
            config = SleepSettings.read(activity)
            assertTrue(config.screen); assertTrue(config.audio)
            val indicator = views(activity.window.decorView).filterIsInstance<QuietLevelIndicator>().single()
            views(activity.window.decorView).filterIsInstance<ScrollView>().single()
                .scrollTo(0, (indicator.parent as View).top)
            screenshots(activity, "quiet-night-independent-controls")
            activity.action("screen filter").performClick()
            config = SleepSettings.read(activity)
            assertFalse(config.screen); assertTrue(config.audio)
            activity.action("quiet steps").performClick()
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
            assertEquals("Media volume, −15 dB", indicator.contentDescription)
            assertFalse(indicator.isClickable)
            assertEquals(2, all.filterIsInstance<Slider>().size)
            screenshots(activity, "quiet-night-audio", audio = true)
        } finally { controller.pause().stop().destroy() }
    }
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun compactHomeKeepsControlsVisibleAboveTheBottomBar() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.write(app, SleepConfig(audio = true))
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            screenshots(activity, "quiet-night-compact")
            val all = views(activity.window.decorView)
            val indicator = all.filterIsInstance<QuietLevelIndicator>().single()
            val buttons = all.filterIsInstance<ExtendedFloatingActionButton>()
            val indicatorBounds = android.graphics.Rect()
            val dockBounds = android.graphics.Rect()
            assertTrue(indicator.getGlobalVisibleRect(indicatorBounds))
            assertEquals(indicator.height, indicatorBounds.height())
            assertTrue((buttons.first().parent as View).getGlobalVisibleRect(dockBounds))
            assertTrue(indicatorBounds.bottom <= dockBounds.top)
            assertTrue(buttons.all { it.width >= 120 * activity.resources.displayMetrics.density })
            val starting = activity.action("quiet steps")
            assertEquals("Starting quiet steps", starting.text.toString())
            assertTrue(starting.layout.lineCount <= 2)
            assertTrue((0 until starting.layout.lineCount).all { starting.layout.getEllipsisCount(it) == 0 })
            assertEquals(starting.text.length, starting.layout.getLineEnd(starting.layout.lineCount - 1))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun helpAndPrivacyRemainAccessibleFromTheMenu() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.write(app, SleepConfig())
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            views(activity.window.decorView).single { it.contentDescription == "More options" }.performClick()
            val popup = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu()
            assertEquals(listOf("How to use", "About & privacy", "Licenses"),
                (0 until popup.menu.size()).map { popup.menu.getItem(it).title.toString() })
            popup.menu.performIdentifierAction(popup.menu.getItem(0).itemId, 0)
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any {
                it.text.contains("Screen filter and Quiet steps work independently")
            })
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun removedAudioTogglesAreGoneFromSettings() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.prefs(app).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.action("quiet steps").performClick()
            val all = views(activity.window.decorView)
            assertTrue(all.filterIsInstance<MaterialSwitch>().none {
                it.contentDescription == "Quiet steps with volume-down" || it.contentDescription == "Floating controls"
            })
            assertTrue(all.filterIsInstance<Button>().none {
                it.text.toString() == "Mute / unmute media" || it.text.toString() == "Floating controls"
            })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun sunsetStartsWithTimeZoneEstimateAndLocationRemainsAnOptionalUpgrade() {
        val previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Winnipeg"))
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.write(app, SleepConfig(mode = ScheduleMode.SUN))
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val button = views(activity.window.decorView).filterIsInstance<Button>()
                .single { it.text.toString() == "Time zone · automatic" }
            assertEquals(SunLocationSource.TIME_ZONE, SleepSettings.read(activity).sunLocationSource)
            assertNull(SleepSettings.read(activity).latitude)
            screenshots(activity, "quiet-night-sunset")
            button.performClick()
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            assertEquals(listOf("Time zone (automatic)", "Use current location", "Enter coordinates"),
                (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() })
            dialog.listView.performItemClick(dialog.listView.getChildAt(0), 0, 0)
            assertEquals(SunLocationSource.TIME_ZONE, SleepSettings.read(activity).sunLocationSource)
        } finally {
            controller.pause().stop().destroy()
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    @Test fun fadeControlIsScheduledOnlyAndDefaultsToInstant() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.write(app, SleepConfig())
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertTrue(views(activity.window.decorView).filterIsInstance<Slider>()
                .none { it.contentDescription == "Fade-in time" })
            views(activity.window.decorView).filterIsInstance<Button>()
                .single { it.text.toString() == "Custom" }.performClick()
            val fade = views(activity.window.decorView).filterIsInstance<Slider>()
                .single { it.contentDescription == "Fade-in time" }
            val start = views(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString().startsWith("START\n") }
            assertEquals(0f, fade.value)
            assertEquals(60f, fade.valueTo)
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text == "Instant" })
            val arguments = android.os.Bundle().apply {
                putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 10f)
            }
            assertTrue(fade.accessibilityNodeProvider!!.performAction(0,
                android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, arguments))
            assertTrue(start.text.toString().startsWith("START FADE-IN\n"))
            assertEquals(10, SleepSettings.read(activity).fadeInMinutes)
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text == "10 min" })
            screenshots(activity, "quiet-night-fade", schedule = true)
            arguments.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0f)
            assertTrue(fade.accessibilityNodeProvider!!.performAction(0,
                android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, arguments))
            assertTrue(start.text.toString().startsWith("START\n"))
        } finally { controller.pause().stop().destroy() }
    }

}
