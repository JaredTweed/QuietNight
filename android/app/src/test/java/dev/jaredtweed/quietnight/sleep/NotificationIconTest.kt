package dev.jaredtweed.quietnight.sleep

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.VectorDrawable
import dev.jaredtweed.quietnight.R
import dev.jaredtweed.quietnight.service.VolumeControlService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationIconTest {
    @Test fun notificationUsesATransparentVectorWithoutTheTileWrapperOrLauncherBackground() {
        val service = Robolectric.buildService(VolumeControlService::class.java).get()
        val method = VolumeControlService::class.java.getDeclaredMethod("notification", SleepConfig::class.java)
        method.isAccessible = true
        val notification = method.invoke(service, SleepConfig(audio = true)) as Notification
        assertEquals(R.drawable.ic_night_symbol, notification.smallIcon.resId)
        assertEquals(service.getColor(R.color.gv_accent), notification.color)
        assertNull(notification.getLargeIcon())
        val icon = notification.smallIcon.loadDrawable(service)!!
        assertTrue(icon is VectorDrawable)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 96, 96)
        icon.draw(Canvas(bitmap))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(95, 95)))
        assertTrue((0 until 96).any { x -> Color.alpha(bitmap.getPixel(x, 48)) > 0 })
        bitmap.recycle()
    }
}
