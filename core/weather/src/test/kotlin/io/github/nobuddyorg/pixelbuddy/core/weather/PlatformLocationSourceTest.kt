package io.github.nobuddyorg.pixelbuddy.core.weather

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PlatformLocationSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(LocationManager::class.java)

    private fun location(
        provider: String,
        latitude: Double,
        longitude: Double,
        ageMinutes: Long = 5,
    ) = Location(provider).apply {
        this.latitude = latitude
        this.longitude = longitude
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - TimeUnit.MINUTES.toNanos(ageMinutes)
    }

    @Test
    fun `nothing without the location permission`() =
        runTest {
            shadowOf(manager).simulateLocation(location("network", 52.52, 13.41))

            assertNull(PlatformLocationSource(app).approximate())
        }

    @Test
    fun `a recent last known location is used, rounded`() =
        runTest {
            shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            SystemClock.setCurrentTimeMillis(TimeUnit.HOURS.toMillis(10))
            shadowOf(manager).simulateLocation(location("network", 52.5243, 13.4105))

            assertEquals(Coordinates(52.5, 13.4), PlatformLocationSource(app).approximate())
        }

    @Test
    fun `stale locations and disabled providers give nothing`() =
        runTest {
            shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            SystemClock.setCurrentTimeMillis(TimeUnit.HOURS.toMillis(10))
            shadowOf(manager).simulateLocation(location("network", 1.0, 2.0, ageMinutes = 120))
            shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            shadowOf(manager).setProviderEnabled(LocationManager.FUSED_PROVIDER, false)

            assertNull(PlatformLocationSource(app).approximate())
        }
}
