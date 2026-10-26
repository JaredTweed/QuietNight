package dev.jaredtweed.quietnight.sleep

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.service.quicksettings.Tile
import dev.jaredtweed.quietnight.R
import dev.jaredtweed.quietnight.service.ScreenFilterTileService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherAndTileTest {
    @Test fun tileTogglesOnlyScreenAndUsesTheNightSymbol() {
        val app = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(ScreenFilterTileService::class.java).create()
        try {
            val service = controller.get()
            for (audio in listOf(false, true)) {
                val original = SleepConfig(audio = audio, warmth = 62, dim = 37, start = 1260, end = 360)
                SleepSettings.write(app, original)
                service.onStartListening()
                assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
                assertEquals(R.drawable.ic_qs_tile, service.qsTile.icon.resId)
                service.onClick()
                assertEquals(original.copy(screen = true), SleepSettings.read(app))
                assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
                service.onClick()
                assertEquals(original, SleepSettings.read(app))
                assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
            }
        } finally {
            // Robolectric 4.14's TileService shadow cannot run Service.onDestroy (invalid shadow cast).
            // This tile owns no listeners; stop listening and let the test sandbox release the stub.
            controller.get().onStopListening()
        }
    }

    @Test fun launcherExposesAMonochromeLayerForSystemTheming() {
        val app = RuntimeEnvironment.getApplication()
        val launcher = app.applicationInfo.loadIcon(app.packageManager)
        assertTrue(launcher is AdaptiveIconDrawable)
        launcher as AdaptiveIconDrawable
        assertNotNull(launcher.monochrome)
        val monochrome = launcher.monochrome!!
        val round = app.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
        assertNotNull(round.monochrome)

        val storeIcon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        launcher.setBounds(0, 0, 512, 512)
        launcher.draw(Canvas(storeIcon))
        val iconFile = File("build/reports/screenshots/quiet-night-icon.png")
        iconFile.parentFile?.mkdirs()
        iconFile.outputStream().use { storeIcon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        storeIcon.recycle()

        // Preview system-selected palettes, without baking these colors into the app's icon.
        val bitmap = Bitmap.createBitmap(960, 260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(235, 233, 238))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 30, 44); textSize = 19f }
        val icons = listOf(launcher,
            AdaptiveIconDrawable(ColorDrawable(Color.rgb(216, 235, 200)), monochrome.constantState!!.newDrawable().mutate().apply { setTint(Color.rgb(30, 57, 21)) }),
            AdaptiveIconDrawable(ColorDrawable(Color.rgb(42, 64, 37)), monochrome.constantState!!.newDrawable().mutate().apply { setTint(Color.rgb(188, 215, 167)) }))
        val labels = listOf("Launcher", "Themed: light", "Themed: dark", "Quick Settings")
        icons.forEachIndexed { i, drawable ->
            drawable.setBounds(i * 240 + 24, 15, i * 240 + 216, 207)
            drawable.draw(canvas)
        }
        paint.color = Color.rgb(42, 64, 37)
        canvas.drawCircle(840f, 111f, 75f, paint)
        app.getDrawable(R.drawable.ic_qs_tile)!!.mutate().apply {
            setTint(Color.rgb(188, 215, 167)); setBounds(792, 63, 888, 159); draw(canvas)
        }
        paint.color = Color.rgb(35, 30, 44)
        labels.forEachIndexed { i, label -> canvas.drawText(label, i * 240 + 30f, 243f, paint) }
        val file = File("build/reports/screenshots/quiet-night-icons.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
