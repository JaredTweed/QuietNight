package com.granularvolume.sleep

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import com.granularvolume.MainActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import java.util.function.Consumer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationRequestTest {
    private lateinit var manager: LocationManager
    private val results = mutableListOf<Consumer<Location>>()
    private val cancellations = mutableListOf<CancellationSignal>()

    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        SleepSettings.prefs(app).edit().clear().commit()
        manager = mock(LocationManager::class.java)
        `when`(manager.getProviders(true)).thenReturn(listOf(LocationManager.NETWORK_PROVIDER))
        doAnswer { call ->
            cancellations += call.getArgument<CancellationSignal>(1)
            results += call.getArgument<Consumer<Location>>(3)
            null
        }.`when`(manager).getCurrentLocation(eq(LocationManager.NETWORK_PROVIDER),
            any(CancellationSignal::class.java), any(Executor::class.java), any())
        shadowOf(app).setSystemService(Context.LOCATION_SERVICE, manager)
    }
    private fun invoke(activity: MainActivity, method: String) {
        MainActivity::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(activity)
    }
    private fun location(latitude: Double) = Location(LocationManager.NETWORK_PROVIDER).apply {
        this.latitude = latitude; longitude = -97.0
    }

    @Test fun delayedLookupCannotOverwriteAManuallyChosenLocation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            invoke(activity, "locate")
            assertEquals(1, results.size)
            invoke(activity, "locationDialog")
            SleepSettings.update(activity) { it.copy(latitude = 20.0, longitude = 30.0) }
            results.single().accept(location(50.0))
            assertTrue(cancellations.single().isCanceled)
            assertEquals(20.0, SleepSettings.read(activity).latitude!!, 0.0)
            assertEquals(30.0, SleepSettings.read(activity).longitude!!, 0.0)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun aNewLookupIgnoresTheOldResultAndAcceptsItsOwnResult() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            invoke(activity, "locate"); invoke(activity, "locate")
            assertEquals(2, results.size)
            results[0].accept(location(10.0))
            assertNull(SleepSettings.read(activity).latitude)
            results[1].accept(location(50.0))
            assertEquals(50.0, SleepSettings.read(activity).latitude!!, 0.0)
            assertTrue(cancellations.all { it.isCanceled })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun permissionRevocationDuringLookupCancelsItsRequest() {
        doAnswer { call ->
            cancellations += call.getArgument<CancellationSignal>(1)
            throw SecurityException("Location permission revoked")
        }.`when`(manager).getCurrentLocation(eq(LocationManager.NETWORK_PROVIDER),
            any(CancellationSignal::class.java), any(Executor::class.java), any())
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            invoke(controller.get(), "locate")
            assertTrue(cancellations.single().isCanceled)
            assertNull(SleepSettings.read(controller.get()).latitude)
        } finally { controller.pause().stop().destroy() }
    }

}
